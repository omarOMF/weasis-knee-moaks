package kneemoaks.weasis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import kneemoaks.model.Dot;
import kneemoaks.ui.MoaksController;
import kneemoaks.ui.Theme;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * A transparent layer on one Weasis view that draws the MOAKS dots and, in dot mode, catches the
 * clicks that place them. Dots are stored in patient coordinates, so the same dot shows on the
 * axial, sagittal and coronal views; off the current slice it is drawn faded with its distance.
 */
final class DotOverlay extends JComponent {

  /** Dots further than this from the slice are not drawn. */
  private static final double MAX_DISTANCE_MM = 10;

  private final ViewCanvas<DicomImageElement> view;
  private final MoaksController ctl;
  private boolean dotMode;
  private final MouseAdapter mouse;
  private Point pressed;

  DotOverlay(ViewCanvas<DicomImageElement> view, MoaksController ctl) {
    this.view = view;
    this.ctl = ctl;
    setOpaque(false);
    // No listeners while dot mode is off, so every click and drag reaches Weasis as usual.
    mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        pressed = SwingUtilities.isLeftMouseButton(e) ? e.getPoint() : null;
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (pressed != null && pressed.distance(e.getPoint()) < 5) {
          click(e.getPoint());
        }
        pressed = null;
      }
    };
  }

  void setDotMode(boolean on) {
    if (on == dotMode) {
      return;
    }
    dotMode = on;
    if (on) {
      addMouseListener(mouse);
      setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    } else {
      removeMouseListener(mouse);
      setCursor(null);
    }
  }

  void detach() {
    setDotMode(false);
    if (getParent() != null) {
      getParent().remove(this);
    }
  }

  // ---- placing ----

  private void click(Point p) {
    DicomImageElement img = view.getImage();
    GeometryOfSlice g = img == null ? null : img.getSliceGeometry();
    if (g == null) {
      return;
    }
    // A click on an existing dot reopens it.
    for (Dot d : ctl.sheet().dots()) {
      Point2D at = toView(d, g);
      if (at != null && at.distance(p) <= 10 && Math.abs(distanceToPlane(vec(d), g)) <= halfThickness(g)) {
        ctl.dotClicked(d, this, p);
        return;
      }
    }
    Point2D ip = view.getImageCoordinatesFromMouse(p.x, p.y);
    Vector3d p3 = g.getPosition(ip);
    Dot d = Dot.create(
        p3.x, p3.y, p3.z,
        img.getFrameOfReferenceUID(),
        WeasisHost.text(img, Tag.SeriesInstanceUID),
        WeasisHost.text(img, Tag.SOPInstanceUID),
        ip.getX(), ip.getY());
    ctl.dotPlaced(d, this, p);
  }

  // ---- geometry ----

  static Vector3d vec(Dot d) {
    return new Vector3d(d.x(), d.y(), d.z());
  }

  /** Signed distance in mm from a point to the plane of a slice. */
  static double distanceToPlane(Vector3d p, GeometryOfSlice g) {
    Vector3d n = g.getNormal();
    Vector3d o = g.getTLHC();
    return (p.x - o.x) * n.x + (p.y - o.y) * n.y + (p.z - o.z) * n.z;
  }

  static boolean sameFrame(Dot d, DicomImageElement img) {
    String a = d.frameOfReferenceUid();
    String b = img.getFrameOfReferenceUID();
    return a == null || a.isEmpty() || b == null || b.isEmpty() || a.equals(b);
  }

  private static double halfThickness(GeometryOfSlice g) {
    double t = g.getSliceThickness();
    double s = g.getVoxelSpacing().z;
    return Math.max(0.5, Math.max(t, s) / 2);
  }

  private Point2D toView(Dot d, GeometryOfSlice g) {
    Point2D ip = g.getImagePosition(vec(d));
    if (ip == null) {
      return null;
    }
    Point vp = view.getMouseCoordinatesFromImage(ip.getX(), ip.getY());
    return vp == null ? null : new Point2D.Double(vp.x, vp.y);
  }

  // ---- painting ----

  @Override
  protected void paintComponent(Graphics graphics) {
    DicomImageElement img = view.getImage();
    GeometryOfSlice g = img == null ? null : img.getSliceGeometry();
    if (g == null || ctl.sheet().dots().isEmpty()) {
      return;
    }
    String seriesStudy = WeasisHost.text(img, Tag.StudyInstanceUID);
    if (!seriesStudy.isEmpty() && !seriesStudy.equals(ctl.sheet().study().studyInstanceUid())) {
      return; // another study in this view
    }
    Graphics2D g2 = Theme.smooth(graphics);
    double half = halfThickness(g);
    for (Dot d : ctl.sheet().dots()) {
      if (!sameFrame(d, img)) {
        continue;
      }
      double dist = distanceToPlane(vec(d), g);
      if (Math.abs(dist) > MAX_DISTANCE_MM) {
        continue;
      }
      Point2D at = toView(d, g);
      if (at == null) {
        continue;
      }
      boolean onSlice = Math.abs(dist) <= half;
      int grade = ctl.dotGrade(d);
      Color c = d.location() == null ? Theme.ACCENT : grade >= 0 ? Theme.grade(grade) : Theme.ACCENT;
      String label = ctl.dotLabel(d);
      if (!onSlice) {
        label = (d.location() == null ? "Dot" : d.location())
            + String.format(Locale.ROOT, " · %.1f mm from slice", Math.abs(dist));
      }
      paintDot(g2, at, c, label, onSlice);
    }
    g2.dispose();
  }

  private static void paintDot(Graphics2D g, Point2D at, Color c, String label, boolean onSlice) {
    double x = at.getX();
    double y = at.getY();
    float alpha = onSlice ? 1f : 0.5f;
    Color cc = Theme.alpha(c, alpha);
    double lx = x + 22;
    double ly = y + 30;
    g.setColor(cc);
    g.setStroke(new BasicStroke(1.5f));
    g.draw(new java.awt.geom.Line2D.Double(x, y, lx, ly));
    if (onSlice) {
      g.setColor(Theme.alpha(c, 0.5));
      g.setStroke(new BasicStroke(2f));
      g.draw(new Ellipse2D.Double(x - 13, y - 13, 26, 26));
    }
    double r = onSlice ? 6 : 4.5;
    g.setColor(cc);
    g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    g.setColor(Theme.alpha(Color.WHITE, alpha));
    g.setStroke(new BasicStroke(2f));
    g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));

    g.setFont(Theme.font(12f, true));
    FontMetrics fm = g.getFontMetrics();
    double w = fm.stringWidth(label) + 24;
    double h = 24;
    RoundRectangle2D.Double pill = new RoundRectangle2D.Double(lx - 4, ly - h / 2, w, h, h, h);
    g.setColor(Theme.alpha(new Color(0x1C1C1E), onSlice ? 0.9f : 0.7f));
    g.fill(pill);
    g.setColor(cc);
    g.setStroke(new BasicStroke(1f));
    g.draw(pill);
    g.setColor(Theme.alpha(Theme.TEXT, onSlice ? 1f : 0.75f));
    g.drawString(label, (float) (lx + 8), (float) (ly + (fm.getAscent() - fm.getDescent()) / 2.0));
  }
}
