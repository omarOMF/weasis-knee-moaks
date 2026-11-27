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
  private String hoverId;

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
        Dot over = dotAt(e.getPoint());
        String id = over == null ? null : over.id();
        if (id == null ? hoverId != null : !id.equals(hoverId)) {
          hoverId = id;
          repaint(); // the full name shows only while the pointer is on a marker
        }
        setCursor(over != null
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
        if (hoverId != null) {
          hoverId = null;
          repaint();
        }
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
    JMenuItem delete = new JMenuItem("Delete marker");
    delete.addActionListener(a -> ctl.deleteDot(d.id()));
    JMenuItem open = new JMenuItem("Score " + ctl.dotLabel(d) + " in the panel");
    open.addActionListener(a -> ctl.dotClicked(d));
    JMenuItem all = new JMenuItem("Delete all markers on this knee…");
    all.addActionListener(a -> {
      int n = ctl.sheet().dots().size();
      int ok = javax.swing.JOptionPane.showConfirmDialog(this,
          "Delete all " + n + " markers on this knee? Scores are kept.", "Delete all markers",
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
      Color c = d.location() == null || grade < 0 ? Theme.ACCENT : Theme.grade(grade);
      boolean hover = d.id().equals(hoverId);
      boolean selected = d.id().equals(ctl.selectedDot());
      if (onSlice) {
        paintMarker(g2, at, c, ctl.markerTag(d), selected);
      } else {
        paintTick(g2, at, c);
      }
      if (hover) {
        String label = ctl.dotLabel(d);
        String scores = d.location() == null ? "" : ctl.sheet().structureSummary(d.location());
        if (!scores.isEmpty()) {
          label += " · " + scores;
        }
        if (!onSlice) {
          label += String.format(Locale.ROOT, " · %.1f mm from slice", Math.abs(dist));
        }
        paintLabel(g2, at, c, label, getWidth());
      }
    }
    g2.dispose();
  }

  /** Open crosshair: four short ticks around the spot, so the finding itself stays visible. */
  private static void paintMarker(Graphics2D g, Point2D at, Color c, String tag, boolean selected) {
    double x = at.getX();
    double y = at.getY();
    double gap = 5;
    double len = 7;
    java.awt.geom.Path2D.Double ticks = new java.awt.geom.Path2D.Double();
    ticks.moveTo(x, y - gap - len);
    ticks.lineTo(x, y - gap);
    ticks.moveTo(x, y + gap);
    ticks.lineTo(x, y + gap + len);
    ticks.moveTo(x - gap - len, y);
    ticks.lineTo(x - gap, y);
    ticks.moveTo(x + gap, y);
    ticks.lineTo(x + gap + len, y);
    // a dark edge keeps the ticks readable on bright tissue
    g.setColor(new Color(0, 0, 0, 150));
    g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(ticks);
    g.setColor(selected ? Color.WHITE : c);
    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(ticks);
    if (selected) {
      g.setColor(c);
      g.draw(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
    }
    outlinedText(g, tag, (float) (x + gap + 4), (float) (y - gap - 3), c, 11.5f);
  }

  /** On another slice: a tiny faint plus, just enough to show a marker is near. */
  private static void paintTick(Graphics2D g, Point2D at, Color c) {
    double x = at.getX();
    double y = at.getY();
    g.setColor(Theme.alpha(c, 0.45));
    g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new java.awt.geom.Line2D.Double(x - 3, y, x + 3, y));
    g.draw(new java.awt.geom.Line2D.Double(x, y - 3, x, y + 3));
  }

  /** The full name, shown only while the pointer is on the marker. */
  private static void paintLabel(Graphics2D g, Point2D at, Color c, String label, int maxX) {
    g.setFont(Theme.font(12f, true));
    FontMetrics fm = g.getFontMetrics();
    double w = fm.stringWidth(label) + 20;
    double h = 22;
    double lx = at.getX() + 16;
    if (lx + w > maxX - 4) {
      lx = Math.max(4, at.getX() - 16 - w); // near the right edge: put it on the left
    }
    double ly = at.getY() + 14;
    RoundRectangle2D.Double pill = new RoundRectangle2D.Double(lx, ly, w, h, h, h);
    g.setColor(new Color(28, 28, 30, 235));
    g.fill(pill);
    g.setColor(c);
    g.setStroke(new BasicStroke(1f));
    g.draw(pill);
    g.setColor(Theme.TEXT);
    g.drawString(label, (float) (lx + 10), (float) (ly + h / 2 + (fm.getAscent() - fm.getDescent()) / 2.0));
  }

  private static void outlinedText(Graphics2D g, String s, float x, float y, Color c, float size) {
    java.awt.Font f = Theme.font(size, true);
    java.awt.font.GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), s);
    java.awt.Shape shape = gv.getOutline(x, y);
    g.setColor(new Color(0, 0, 0, 170));
    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(shape);
    g.setColor(c);
    g.fill(shape);
  }
}
