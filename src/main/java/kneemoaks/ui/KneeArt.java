package kneemoaks.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import kneemoaks.model.Moaks;
import kneemoaks.model.ScoreSheet;

/**
 * The shaded drawing of the knee's joint surfaces, opened out: patella from behind, femur from
 * below, tibia from above. It looks three-dimensional but is a flat drawing. The panel shows the
 * part that holds the selected structure, with that structure lit and the rest dimmed; each
 * subregion is coloured by its worst cartilage or marrow score.
 */
public final class KneeArt extends JComponent {

  private record Region(String key, Shape shape, Point2D label, boolean marker) {}

  private static final Color BONE_1 = new Color(0xF4EBDB);
  private static final Color BONE_2 = new Color(0xDCCBAE);
  private static final Color BONE_3 = new Color(0xB49E7E);
  private static final Color CART_1 = new Color(0xF3F8FD);
  private static final Color CART_2 = new Color(0xC9DCEE);
  private static final Color CART_3 = new Color(0x9DB8D3);
  private static final Color LABEL = new Color(0x41566E);
  private static final Color CAPTION = new Color(0x8E8E93);

  private static final Rectangle2D FULL = new Rectangle2D.Double(0, 0, 340, 520);
  private static final Rectangle2D PATELLA = new Rectangle2D.Double(40, 6, 260, 176);
  private static final Rectangle2D FEMUR = new Rectangle2D.Double(30, 180, 280, 196);
  private static final Rectangle2D TIBIA = new Rectangle2D.Double(30, 378, 280, 142);

  private final ScoreSheet sheet;
  private final List<Region> regions = new ArrayList<>();
  private String structure;
  private String hover;
  private Rectangle2D crop = FULL;
  private AffineTransform toView = new AffineTransform();
  private Consumer<String> onPick = k -> {};

  public KneeArt(ScoreSheet sheet) {
    this.sheet = sheet;
    setOpaque(false);
    buildRegions();
    MouseAdapter m = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        String k = keyAt(e.getX(), e.getY());
        if (k == null ? hover != null : !k.equals(hover)) {
          hover = k;
          setCursor(java.awt.Cursor.getPredefinedCursor(
              k == null ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
          Moaks.Location loc = k == null ? null : Moaks.location(k);
          setToolTipText(loc == null ? null : loc.name());
          repaint();
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hover = null;
        repaint();
      }

      @Override
      public void mousePressed(MouseEvent e) {
        String k = keyAt(e.getX(), e.getY());
        String st = k == null ? null : Moaks.structureOfLocation(k);
        if (st != null) {
          onPick.accept(st);
        }
      }
    };
    addMouseListener(m);
    addMouseMotionListener(m);
    sheet.addListener(this::repaint);
  }

  @Override
  public javax.accessibility.AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJComponent() {
        @Override
        public javax.accessibility.AccessibleRole getAccessibleRole() {
          return javax.accessibility.AccessibleRole.PANEL;
        }
      };
      accessibleContext.setAccessibleName("Knee drawing");
    }
    return accessibleContext;
  }

  /** Clicking a subregion picks its structure. */
  public void onPick(Consumer<String> c) {
    onPick = c;
  }

  /** Shows the part of the knee that holds this structure (or the whole drawing for null). */
  public void setStructure(String key) {
    structure = key;
    Moaks.Structure st = Moaks.structure(key);
    String part = st == null ? "" : st.part();
    crop = switch (part) {
      case "Patella" -> PATELLA;
      case "Trochlea", "Femur" -> FEMUR;
      case "Tibia" -> TIBIA;
      default -> FULL;
    };
    revalidate();
    repaint();
  }

  // ---- geometry ----

  private void buildRegions() {
    surface("PM", "M170 42c-40 2-66 20-68 44 0 24 30 46 68 54z", 118, 96);
    surface("PL", "M170 42c46 2 74 22 74 46-2 24-36 46-74 52z", 200, 96);
    surface("MFa", "M82 208c28-8 58-6 88 4v38h-24c-8 0-14 4-16 10H80z", 104, 236);
    surface("LFa", "M258 208c-28-8-58-6-88 4v38h24c8 0 14 4 16 10h50z", 208, 236);
    surface("MFc", "M78 266h62v46H78z", 96, 294);
    surface("LFc", "M200 266h62v46h-62z", 218, 294);
    surface("MFp", "M78 316h62v16c0 16-12 26-30 26s-32-10-32-26z", 96, 342);
    surface("LFp", "M200 316h62v16c0 16-14 26-32 26s-30-10-30-26z", 218, 342);
    Ellipse2D med = new Ellipse2D.Double(118 - 46, 446 - 50, 92, 100);
    Ellipse2D lat = new Ellipse2D.Double(222 - 42, 446 - 44, 84, 88);
    band("MTa", med, 390, 430, 102, 420);
    band("MTc", med, 430, 462, 102, 451);
    band("MTp", med, 462, 502, 102, 484);
    band("LTa", lat, 398, 432, 208, 422);
    band("LTc", lat, 432, 462, 208, 451);
    band("LTp", lat, 462, 496, 208, 484);
    double[][] pos = {
      {170, 26}, {170, 166}, {74, 88}, {266, 88},
      {78, 204}, {262, 204}, {64, 290}, {276, 290}, {92, 362}, {248, 362},
      {52, 452}, {288, 452}
    };
    String[] keys = {
      "OstPS", "OstPI", "OstPM", "OstPL", "OstTrM", "OstTrL", "OstFCM", "OstFCL",
      "OstFPM", "OstFPL", "OstTM", "OstTL"
    };
    for (int i = 0; i < keys.length; i++) {
      Shape c = new Ellipse2D.Double(pos[i][0] - 8, pos[i][1] - 8, 16, 16);
      regions.add(new Region(keys[i], c, new Point2D.Double(pos[i][0], pos[i][1]), true));
    }
  }

  private void surface(String key, String d, double lx, double ly) {
    regions.add(new Region(key, SvgPath.parse(d), new Point2D.Double(lx, ly), false));
  }

  private void band(String key, Ellipse2D plate, double y0, double y1, double lx, double ly) {
    Area a = new Area(plate);
    a.intersect(new Area(new Rectangle2D.Double(plate.getX() - 2, y0, plate.getWidth() + 4, y1 - y0)));
    regions.add(new Region(key, a, new Point2D.Double(lx, ly), false));
  }

  private boolean focusing() {
    return Moaks.structure(structure) != null && !Moaks.WHOLE_KNEE.equals(structure);
  }

  private boolean inFocus(String locationKey) {
    Moaks.Structure st = Moaks.structure(structure);
    if (st == null) {
      return true;
    }
    for (String id : st.itemIds()) {
      if (Moaks.item(id).location().equals(locationKey)) {
        return true;
      }
    }
    return false;
  }

  /** Worst cartilage or marrow grade of a subregion; -1 if neither entered. */
  private int subregionGrade(String key) {
    int g = -1;
    if (Moaks.item("cart." + key) != null) {
      g = Math.max(g, sheet.grade("cart." + key));
    }
    if (Moaks.item("bml." + key) != null) {
      g = Math.max(g, sheet.grade("bml." + key));
    }
    return g;
  }

  // ---- layout and hit testing ----

  @Override
  public Dimension getPreferredSize() {
    int w = getParent() == null ? 320 : Math.max(220, Math.min(340, getParent().getWidth() - 8));
    return new Dimension(w, (int) Math.round(w * crop.getHeight() / crop.getWidth()));
  }

  private void updateTransform() {
    double s = Math.min(getWidth() / crop.getWidth(), getHeight() / crop.getHeight());
    double ox = (getWidth() - crop.getWidth() * s) / 2 - crop.getX() * s;
    double oy = (getHeight() - crop.getHeight() * s) / 2 - crop.getY() * s;
    toView = new AffineTransform(s, 0, 0, s, ox, oy);
  }

  private String keyAt(int x, int y) {
    updateTransform();
    Point2D p;
    try {
      p = toView.inverseTransform(new Point2D.Double(x, y), null);
    } catch (NoninvertibleTransformException e) {
      return null;
    }
    if (!crop.contains(p)) {
      return null;
    }
    for (int i = regions.size() - 1; i >= 0; i--) {
      Region r = regions.get(i);
      if (r.shape().contains(p) && (!r.marker() || (focusing() && inFocus(r.key())))) {
        return r.key();
      }
    }
    return null;
  }

  // ---- painting ----

  @Override
  protected void paintComponent(Graphics g0) {
    updateTransform();
    Graphics2D g = Theme.smooth(g0);
    g.transform(toView);
    g.clip(crop);
    if (crop == FULL || crop == PATELLA) {
      caption(g, "PATELLA · FROM BEHIND", 46, 20);
    }
    if (crop == FULL || crop == FEMUR) {
      caption(g, "FEMUR · FROM BELOW", 36, 194);
    }
    if (crop == FULL || crop == TIBIA) {
      caption(g, "TIBIA · FROM ABOVE", 36, 392);
    }
    bone(g, SvgPath.parse("M170 30c60 0 92 24 92 56s-42 66-92 74c-50-8-92-42-92-74s32-56 92-56z"));
    bone(g, SvgPath.parse("M66 214c0-14 34-20 104-20s104 6 104 20l6 108c0 30-22 44-46 44-20 0-32-14-34-32l-4-50c-4-12-14-16-26-16s-22 4-26 16l-4 50c-2 18-14 32-34 32-24 0-46-14-46-44z"));
    bone(g, SvgPath.parse("M58 430c0-26 40-30 112-30s112 4 112 30l4 50c0 26-34 30-116 30s-116-4-116-30z"));
    for (Region r : regions) {
      if (r.marker()) {
        continue;
      }
      g.setPaint(cartilagePaint(r.shape().getBounds2D(), subregionGrade(r.key())));
      g.fill(r.shape());
      g.setColor(new Color(255, 255, 255, 110));
      g.setStroke(new BasicStroke(1f));
      g.draw(r.shape());
    }
    g.setColor(new Color(0x7E95AD));
    g.setStroke(new BasicStroke(1.5f));
    g.draw(SvgPath.parse("M170 42v98"));
    g.draw(SvgPath.parse("M170 212v38"));
    g.setColor(new Color(255, 255, 255, 140));
    g.setStroke(new BasicStroke(1f));
    g.draw(SvgPath.parse("M72 430h92M72 462h92M180 432h84M180 462h84"));
    g.setPaint(new LinearGradientPaint(0, 396, 0, 500, new float[] {0, 1},
        new Color[] {new Color(0xDDE3EA), new Color(0xA9B4C2)}));
    g.setStroke(new BasicStroke(9, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(SvgPath.parse("M120 398c-34 0-52 22-52 48s18 50 52 50"));
    g.draw(SvgPath.parse("M240 404c22 6 32 22 32 42s-14 40-50 42c-30-2-46-20-46-42 0-14 8-28 22-36"));
    g.setStroke(new BasicStroke(1f));
    Shape spines = SvgPath.parse("M164 440c4-10 8-10 10-2 2-8 6-8 10 2z");
    g.setColor(new Color(0xC8B596));
    g.fill(spines);
    g.setColor(new Color(0x9C8768));
    g.draw(spines);

    for (Region r : regions) {
      if (!r.marker()) {
        text(g, r.key(), r.label(), subregionGrade(r.key()) >= 0 ? Theme.TEXT_DARK : LABEL, 11.5f, false);
      }
    }
    if (focusing()) {
      g.setColor(Theme.alpha(Theme.PANEL, 0.62));
      for (Region r : regions) {
        if (!r.marker() && !inFocus(r.key())) {
          g.fill(r.shape());
        }
      }
      for (Region r : regions) {
        if (r.marker() && inFocus(r.key())) {
          marker(g, r);
        }
      }
    }
    for (Region r : regions) {
      boolean focus = !r.marker() && focusing() && inFocus(r.key());
      boolean hov = r.key().equals(hover);
      if (focus || hov) {
        g.setColor(focus ? Theme.ACCENT : Theme.alpha(Color.WHITE, 0.85));
        g.setStroke(new BasicStroke(focus ? 2.5f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(r.shape());
      }
    }
    if (crop == FULL || crop == TIBIA) {
      caption(g, "Medial", 40, 514);
      caption(g, "Lateral", 262, 514);
    } else {
      caption(g, "Medial", crop.getX() + 6, crop.getMaxY() - 6);
      caption(g, "Lateral", crop.getMaxX() - 46, crop.getMaxY() - 6);
    }
    g.dispose();
  }

  private void marker(Graphics2D g, Region r) {
    String id = "ost." + r.key();
    boolean entered = sheet.isEntered(id);
    int grade = sheet.grade(id);
    Ellipse2D c = (Ellipse2D) r.shape();
    Color fill = entered ? Theme.grade(Math.max(grade, 0)) : new Color(0x48484A);
    g.setColor(r.key().equals(hover) ? fill.brighter() : fill);
    g.fill(c);
    g.setColor(Theme.PANEL);
    g.setStroke(new BasicStroke(1.5f));
    g.draw(c);
    if (entered) {
      text(g, String.valueOf(Math.max(grade, 0)), r.label(), Theme.TEXT_DARK, 9.5f, true);
    }
  }

  private static Paint cartilagePaint(Rectangle2D b, int grade) {
    Color c1;
    Color c2;
    Color c3;
    if (grade < 0) {
      c1 = CART_1;
      c2 = CART_2;
      c3 = CART_3;
    } else {
      Color base = Theme.grade(grade);
      c1 = mix(base, Color.WHITE, 0.45);
      c2 = base;
      c3 = mix(base, Color.BLACK, 0.18);
    }
    float r = (float) Math.max(1, 0.9 * Math.max(b.getWidth(), b.getHeight()));
    Point2D center = new Point2D.Double(b.getX() + 0.35 * b.getWidth(), b.getY() + 0.3 * b.getHeight());
    return new RadialGradientPaint(center, r, new float[] {0f, 0.6f, 1f}, new Color[] {c1, c2, c3});
  }

  private static void bone(Graphics2D g, Shape s) {
    Graphics2D sg = (Graphics2D) g.create();
    sg.translate(0, 7);
    for (int i = 7; i >= 1; i--) {
      sg.setColor(new Color(0, 0, 0, 16));
      sg.setStroke(new BasicStroke(i * 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      sg.draw(s);
    }
    sg.setColor(new Color(0, 0, 0, 90));
    sg.fill(s);
    sg.dispose();
    Rectangle2D b = s.getBounds2D();
    g.setPaint(new LinearGradientPaint(
        new Point2D.Double(b.getX(), b.getY()),
        new Point2D.Double(b.getMaxX() + 0.001, b.getMaxY() + 0.001),
        new float[] {0f, 0.55f, 1f},
        new Color[] {BONE_1, BONE_2, BONE_3}));
    g.fill(s);
  }

  private static Color mix(Color a, Color b, double t) {
    return new Color(
        (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t),
        (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t),
        (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t));
  }

  private static void caption(Graphics2D g, String s, double x, double y) {
    g.setFont(Theme.font(10f, true));
    g.setColor(CAPTION);
    g.drawString(s, (float) x, (float) y);
  }

  private static void text(Graphics2D g, String s, Point2D at, Color c, float size, boolean centred) {
    g.setFont(Theme.font(size, true));
    g.setColor(c);
    if (centred) {
      FontMetrics fm = g.getFontMetrics();
      g.drawString(s, (float) (at.getX() - fm.stringWidth(s) / 2.0),
          (float) (at.getY() + (fm.getAscent() - fm.getDescent()) / 2.0));
    } else {
      g.drawString(s, (float) at.getX(), (float) at.getY());
    }
  }
}
