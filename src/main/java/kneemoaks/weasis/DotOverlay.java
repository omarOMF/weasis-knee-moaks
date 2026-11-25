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
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
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
 * A transparent layer on one Weasis view that draws the MOAKS dots, opens a dot's structure in
 * the panel when it is clicked, and in dot mode places new dots. Dots are stored in patient coordinates, so the same dot shows on the
 * axial, sagittal and coronal views; off the current slice it is drawn faded with its distance.
 */
final class DotOverlay extends JComponent {

  /** Dots further than this from the slice are not drawn. */
  private static final double MAX_DISTANCE_MM = 10;

  private final ViewCanvas<DicomImageElement> view;
  private final MoaksController ctl;
  private boolean dotMode;
  private Point pressed;
  private boolean ours;
  private boolean menuShown;

  DotOverlay(ViewCanvas<DicomImageElement> view, MoaksController ctl) {
    this.view = view;
    this.ctl = ctl;
    setOpaque(false);
    // The layer listens all the time so a click on a dot opens it in the panel. Every other
    // mouse event is passed on to the Weasis view underneath, so scrolling, window/level,
    // panning and Weasis's own menus work as usual. (Wheel events go straight to the view.)
    MouseAdapter mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        pressed = e.getPoint();
        Dot hit = dotAt(e.getPoint());
        ours = dotMode && SwingUtilities.isLeftMouseButton(e) || hit != null;
        if (!ours) {
          forward(e);
          return;
        }
        // Right-click, two-finger click or Control-click on a dot: the menu (macOS reports the
        // popup trigger on press).
        if (hit != null && (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e) || e.isControlDown())) {
          menuShown = true;
          showMenu(hit, e.getPoint());
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (!ours) {
          forward(e);
          pressed = null;
          return;
        }
        boolean click = pressed != null && pressed.distance(e.getPoint()) < 5;
        pressed = null;
        ours = false;
        if (menuShown) {
          menuShown = false;
          return;
        }
        if (!click) {
          return;
        }
        Dot hit = dotAt(e.getPoint());
        if (hit != null && (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger())) {
          showMenu(hit, e.getPoint());
        } else if (hit != null && e.getClickCount() >= 2) {
          ctl.openDot(hit); // double-click: open the scores in the panel
        } else if (hit != null) {
          ctl.dotClicked(hit);
        } else if (dotMode && SwingUtilities.isLeftMouseButton(e)) {
          place(e.getPoint());
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (!ours && dotAt(e.getPoint()) == null && !dotMode) {
          forward(e);
        }
      }

      @Override
      public void mouseDragged(MouseEvent e) {
        if (!ours) {
          forward(e);
        }
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        setCursor(dotAt(e.getPoint()) != null
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : dotMode ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);
        forward(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        forward(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        forward(e);
      }
    };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  private void forward(MouseEvent e) {
    java.awt.Container parent = getParent();
    if (parent != null) {
      parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, parent));
    }
  }

  private void showMenu(Dot d, Point p) {
    JPopupMenu menu = new JPopupMenu();
    JMenuItem delete = new JMenuItem("Delete dot");
    delete.addActionListener(a -> ctl.deleteDot(d.id()));
    JMenuItem open = new JMenuItem("Score " + ctl.dotLabel(d) + " in the panel");
    open.addActionListener(a -> ctl.dotClicked(d));
    JMenuItem all = new JMenuItem("Delete all dots on this knee…");
    all.addActionListener(a -> {
      int n = ctl.sheet().dots().size();
      int ok = javax.swing.JOptionPane.showConfirmDialog(this,
          "Delete all " + n + " dots on this knee? Scores are kept.", "Delete all dots",
          javax.swing.JOptionPane.OK_CANCEL_OPTION);
      if (ok == javax.swing.JOptionPane.OK_OPTION) {
        ctl.deleteAllDots();
      }
    });
    menu.add(delete);
    menu.add(open);
    menu.addSeparator();
    menu.add(all);
    menu.show(this, p.x, p.y);
  }

  /** Delete or Backspace on the view removes the dot last clicked. Added to the Weasis view. */
  final java.awt.event.KeyListener keys = new java.awt.event.KeyAdapter() {
    @Override
    public void keyPressed(java.awt.event.KeyEvent e) {
      int k = e.getKeyCode();
      String sel = ctl.selectedDot();
      if (sel != null && ctl.sheet().dot(sel) != null
          && (k == java.awt.event.KeyEvent.VK_DELETE || k == java.awt.event.KeyEvent.VK_BACK_SPACE)) {
        ctl.deleteDot(sel);
        e.consume();
      } else if (k == java.awt.event.KeyEvent.VK_ESCAPE && dotMode) {
        ctl.setDotMode(false);
      }
    }
  };

  void setDotMode(boolean on) {
    dotMode = on;
    setCursor(on ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);
  }

  void detach() {
    setDotMode(false);
    if (getParent() != null) {
      getParent().removeKeyListener(keys);
      getParent().remove(this);
    }
  }

  // ---- placing ----

  private void place(Point p) {
    DicomImageElement img = view.getImage();
    GeometryOfSlice g = img == null ? null : img.getSliceGeometry();
    if (g == null) {
      return;
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

  /** The dot drawn under a point of this view, if any. */
  private Dot dotAt(Point p) {
    DicomImageElement img = view.getImage();
    GeometryOfSlice g = img == null ? null : img.getSliceGeometry();
    if (g == null || !ctl.dotsVisible()) {
      return null;
    }
    for (Dot d : ctl.sheet().dots()) {
      Point2D at = toView(d, g);
      if (at != null && at.distance(p) <= 10 && sameFrame(d, img)
          && Math.abs(distanceToPlane(vec(d), g)) <= MAX_DISTANCE_MM) {
        return d;
      }
    }
    return null;
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
    if (g == null || ctl.sheet().dots().isEmpty() || !ctl.dotsVisible()) {
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
      if (d.id().equals(ctl.selectedDot())) {
        g2.setColor(java.awt.Color.WHITE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Double(at.getX() - 11, at.getY() - 11, 22, 22));
      }
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
