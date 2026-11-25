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
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import kneemoaks.model.Moaks;
import kneemoaks.model.Moaks.Tab;
import kneemoaks.model.ScoreSheet;

/**
 * The shaded knee illustrations the reader clicks on. They look three-dimensional but are flat
 * drawings: the joint surfaces opened out (patella from behind, femur from below, tibia from
 * above), the menisci on the tibial plateau, and a side view for the soft tissues.
 */
public final class KneeArt extends JComponent {

  /** What colours the bone and cartilage drawing. */
  public enum Layer {
    CARTILAGE("Cartilage", "cart."),
    MARROW("Marrow", "bml."),
    OSTEOPHYTES("Osteophytes", "ost.");

    public final String label;
    final String prefix;

    Layer(String label, String prefix) {
      this.label = label;
      this.prefix = prefix;
    }
  }

  private enum Kind {
    SURFACE,
    BAND,
    MARKER,
    PIN
  }

  private record Region(String key, Shape shape, Point2D label, String text, Kind kind, Shape draw) {}

  private static final Color BONE_1 = new Color(0xF4EBDB);
  private static final Color BONE_2 = new Color(0xDCCBAE);
  private static final Color BONE_3 = new Color(0xB49E7E);
  private static final Color CART_1 = new Color(0xF3F8FD);
  private static final Color CART_2 = new Color(0xC9DCEE);
  private static final Color CART_3 = new Color(0x9DB8D3);
  private static final Color MEN = new Color(0xC9D2DD);
  private static final Color LABEL = new Color(0x41566E);
  private static final Color CAPTION = new Color(0x8E8E93);

  private final ScoreSheet sheet;
  private Tab tab = Tab.BONE;
  private Layer layer = Layer.CARTILAGE;
  private String selected;
  private String hover;
  private Consumer<String> onPick = k -> {};
  private final List<Region> regions = new ArrayList<>();
  private double vw;
  private double vh;
  private AffineTransform toView = new AffineTransform();

  public KneeArt(ScoreSheet sheet) {
    this.sheet = sheet;
    setOpaque(false);
    build();
    MouseAdapter m = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        String k = keyAt(e.getX(), e.getY());
        if (k == null ? hover != null : !k.equals(hover)) {
          hover = k;
          setCursor(java.awt.Cursor.getPredefinedCursor(
              k == null ? java.awt.Cursor.DEFAULT_CURSOR : java.awt.Cursor.HAND_CURSOR));
          setToolTipText(k == null ? null : tooltip(k));
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
        if (k != null) {
          onPick.accept(k);
        }
      }
    };
    addMouseListener(m);
    addMouseMotionListener(m);
    sheet.addListener(this::repaint);
    getAccessibleContext().setAccessibleName("Knee illustration");
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
    }
    return accessibleContext;
  }

  public void onPick(Consumer<String> c) {
    onPick = c;
  }

  public void setTab(Tab t) {
    tab = t;
    build();
    revalidate();
    repaint();
  }

  public void setLayer(Layer l) {
    layer = l;
    build();
    repaint();
  }

  public Layer layer() {
    return layer;
  }

  public void setSelected(String key) {
    selected = key;
    repaint();
  }

  private String tooltip(String key) {
    Moaks.Location loc = Moaks.location(key);
    String s = sheet.summary(key);
    return (loc == null ? key : loc.name()) + (s.isEmpty() ? "" : " · " + s);
  }

  // ---- geometry ----

  private void build() {
    regions.clear();
    switch (tab) {
      case BONE -> buildBone();
      case MENISCI -> buildMenisci();
      case SOFT -> buildSoft();
    }
  }

  private void buildBone() {
    vw = 340;
    vh = 520;
    if (layer == Layer.OSTEOPHYTES) {
      double[][] pos = {
        {170, 26}, {170, 166}, {74, 88}, {266, 88}, // patella sup, inf, med, lat
        {78, 204}, {262, 204}, {64, 290}, {276, 290}, {92, 362}, {248, 362}, // femur
        {52, 452}, {288, 452} // tibia
      };
      String[] keys = {
        "OstPS", "OstPI", "OstPM", "OstPL", "OstTrM", "OstTrL", "OstFCM", "OstFCL",
        "OstFPM", "OstFPL", "OstTM", "OstTL"
      };
      for (int i = 0; i < keys.length; i++) {
        Shape c = new Ellipse2D.Double(pos[i][0] - 10, pos[i][1] - 10, 20, 20);
        regions.add(new Region(keys[i], c, null, null, Kind.MARKER, c));
      }
      return;
    }
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
    if (layer == Layer.MARROW) {
      Shape tss = new RoundRectangle2D.Double(158, 424, 34, 26, 10, 10);
      regions.add(new Region("TSS", tss, new Point2D.Double(164, 462), "TSS", Kind.SURFACE, tss));
    }
  }

  private void surface(String key, String d, double lx, double ly) {
    Shape s = SvgPath.parse(d);
    regions.add(new Region(key, s, new Point2D.Double(lx, ly), key, Kind.SURFACE, s));
  }

  private void band(String key, Ellipse2D plate, double y0, double y1, double lx, double ly) {
    Area a = new Area(plate);
    a.intersect(new Area(new Rectangle2D.Double(plate.getX() - 2, y0, plate.getWidth() + 4, y1 - y0)));
    regions.add(new Region(key, a, new Point2D.Double(lx, ly), key, Kind.SURFACE, a));
  }

  private void buildMenisci() {
    vw = 400;
    vh = 330;
    segment("MM-AH", "M150 92c-44-2-76 20-84 52", 104, 112, "AH");
    segment("MM-B", "M64 154c-4 20 0 40 10 58", 80, 186, "Body");
    segment("MM-PH", "M82 224c18 22 48 34 78 30", 112, 236, "PH");
    segment("LM-AH", "M256 102c30-2 56 16 66 42", 276, 128, "AH");
    segment("LM-B", "M330 158c4 22 0 40-10 54", 290, 186, "Body");
    segment("LM-PH", "M310 222c-18 22-50 28-76 18", 262, 226, "PH");
    arrow("ext.MM-med", "MM-B", "M30 176h-18m6-6-6 6 6 6", 2, 176);
    arrow("ext.MM-ant", "MM-AH", "M100 66V44m-6 6 6-6 6 6", 100, 40);
    arrow("ext.LM-ant", "LM-AH", "M300 66V44m-6 6 6-6 6 6", 300, 40);
    arrow("ext.LM-lat", "LM-B", "M370 176h18m-6-6 6 6-6 6", 392, 176);
  }

  private void segment(String key, String d, double lx, double ly, String text) {
    Shape line = SvgPath.parse(d);
    Shape hit = new BasicStroke(26, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(line);
    regions.add(new Region(key, hit, new Point2D.Double(lx, ly), text, Kind.BAND, line));
  }

  private void arrow(String itemId, String key, String d, double cx, double cy) {
    Shape line = SvgPath.parse(d);
    Shape hit = new Ellipse2D.Double(cx - 16, cy - 16, 32, 32);
    // the text slot carries the extrusion item this arrow colours by
    regions.add(new Region(key, hit, new Point2D.Double(cx, cy), itemId, Kind.MARKER, line));
  }

  private static final Object[][] PINS = {
    {"EFF", 183, 70}, {"HOF", 158, 222}, {"ACL", 214, 206}, {"PCL", 250, 188},
    {"PT", 116, 236}, {"POP", 322, 196}, {"PPB", 100, 136}, {"IPB", 140, 272},
    {"PES", 186, 302}, {"ITB", 282, 112}, {"GTF", 326, 270}, {"GCR", 232, 230},
    {"GOT", 356, 238}, {"LB", 232, 156}
  };

  /** Number shown on each soft tissue pin, in list order. */
  public static int pinNumber(String key) {
    for (int i = 0; i < PINS.length; i++) {
      if (PINS[i][0].equals(key)) {
        return i + 1;
      }
    }
    return 0;
  }

  public static List<String> softOrder() {
    List<String> l = new ArrayList<>();
    for (Object[] p : PINS) {
      l.add((String) p[0]);
    }
    return l;
  }

  private void buildSoft() {
    vw = 400;
    vh = 330;
    for (int i = 0; i < PINS.length; i++) {
      double x = ((Number) PINS[i][1]).doubleValue();
      double y = ((Number) PINS[i][2]).doubleValue();
      Shape c = new Ellipse2D.Double(x - 11, y - 11, 22, 22);
      regions.add(new Region((String) PINS[i][0], c, new Point2D.Double(x, y),
          String.valueOf(i + 1), Kind.PIN, c));
    }
  }

  // ---- layout and hit testing ----

  @Override
  public Dimension getPreferredSize() {
    int w = getParent() == null ? 320 : Math.max(240, Math.min(380, getParent().getWidth() - 8));
    return new Dimension(w, (int) Math.round(w * vh / vw));
  }

  private void updateTransform() {
    double s = Math.min(getWidth() / vw, getHeight() / vh);
    double ox = (getWidth() - vw * s) / 2;
    double oy = (getHeight() - vh * s) / 2;
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
    for (int i = regions.size() - 1; i >= 0; i--) {
      if (regions.get(i).shape().contains(p)) {
        return regions.get(i).key();
      }
    }
    return null;
  }

  // ---- painting ----

  @Override
  protected void paintComponent(Graphics g) {
    updateTransform();
    Graphics2D g2 = Theme.smooth(g);
    g2.transform(toView);
    switch (tab) {
      case BONE -> paintBone(g2);
      case MENISCI -> paintMenisci(g2);
      case SOFT -> paintSoft(g2);
    }
    g2.dispose();
  }

  private void paintBone(Graphics2D g) {
    caption(g, "PATELLA · FROM BEHIND", 16, 22);
    caption(g, "FEMUR · FROM BELOW", 16, 196);
    caption(g, "TIBIA · FROM ABOVE", 16, 392);
    Shape patella = SvgPath.parse("M170 30c60 0 92 24 92 56s-42 66-92 74c-50-8-92-42-92-74s32-56 92-56z");
    Shape femur = SvgPath.parse("M66 214c0-14 34-20 104-20s104 6 104 20l6 108c0 30-22 44-46 44-20 0-32-14-34-32l-4-50c-4-12-14-16-26-16s-22 4-26 16l-4 50c-2 18-14 32-34 32-24 0-46-14-46-44z");
    Shape tibia = SvgPath.parse("M58 430c0-26 40-30 112-30s112 4 112 30l4 50c0 26-34 30-116 30s-116-4-116-30z");
    bone(g, patella);
    bone(g, femur);
    bone(g, tibia);
    boolean ost = layer == Layer.OSTEOPHYTES;
    if (ost) {
      // Plain cartilage so the osteophyte markers stand out.
      for (String[] s : new String[][] {
          {"M170 42c-40 2-66 20-68 44 0 24 30 46 68 54z"}, {"M170 42c46 2 74 22 74 46-2 24-36 46-74 52z"},
          {"M82 208c28-8 58-6 88 4v38h-24c-8 0-14 4-16 10H80z"}, {"M258 208c-28-8-58-6-88 4v38h24c8 0 14 4 16 10h50z"},
          {"M78 266h62v46H78z"}, {"M200 266h62v46h-62z"},
          {"M78 316h62v16c0 16-12 26-30 26s-32-10-32-26z"}, {"M200 316h62v16c0 16-14 26-32 26s-30-10-30-26z"}}) {
        Shape sh = SvgPath.parse(s[0]);
        g.setPaint(cartilagePaint(sh.getBounds2D(), -1));
        g.fill(sh);
      }
      g.setPaint(cartilagePaint(new Rectangle2D.Double(72, 396, 92, 100), -1));
      g.fill(new Ellipse2D.Double(72, 396, 92, 100));
      g.setPaint(cartilagePaint(new Rectangle2D.Double(180, 402, 84, 88), -1));
      g.fill(new Ellipse2D.Double(180, 402, 84, 88));
    }
    for (Region r : regions) {
      if (r.kind() != Kind.SURFACE) {
        continue;
      }
      int grade = sheet.grade(layer.prefix + r.key());
      g.setPaint(cartilagePaint(r.shape().getBounds2D(), grade));
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
    // menisci and tibial spines, for orientation
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
      if (r.kind() == Kind.SURFACE) {
        int grade = sheet.grade(layer.prefix + r.key());
        text(g, r.text(), r.label(), grade >= 0 ? Theme.TEXT_DARK : LABEL, 11.5f);
      }
    }
    if (ost) {
      for (Region r : regions) {
        int grade = sheet.grade("ost." + r.key());
        marker(g, (Ellipse2D) r.shape(), grade, sheet.isEntered("ost." + r.key()));
      }
    }
    caption(g, "Medial", 40, 514);
    caption(g, "Lateral", 262, 514);
    highlight(g);
    badge(g);
  }

  private void paintMenisci(Graphics2D g) {
    caption(g, "TIBIA · FROM ABOVE · ANTERIOR UP", 18, 26);
    bone(g, SvgPath.parse("M46 110c0-40 60-48 154-48s154 8 154 48l6 104c0 44-50 56-160 56S40 258 40 214z"));
    Ellipse2D mp = new Ellipse2D.Double(68, 90, 120, 152);
    Ellipse2D lp = new Ellipse2D.Double(216, 100, 112, 132);
    g.setPaint(cartilagePaint(mp.getBounds2D(), -1));
    g.fill(mp);
    g.setPaint(cartilagePaint(lp.getBounds2D(), -1));
    g.fill(lp);
    Shape spines = SvgPath.parse("M188 168c6-14 10-14 12-2 2-12 6-12 12 2z");
    g.setColor(new Color(0xC8B596));
    g.fill(spines);
    g.setColor(new Color(0x9C8768));
    g.draw(spines);
    for (Region r : regions) {
      if (r.kind() == Kind.BAND) {
        if (r.key().equals(selected)) {
          g.setColor(Theme.alpha(Theme.ACCENT, 0.4));
          g.setStroke(new BasicStroke(26, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
          g.draw(r.draw());
        }
        int grade = sheet.grade("men." + r.key());
        g.setColor(grade < 0 ? MEN : Theme.grade(grade));
        if (r.key().equals(hover) && !r.key().equals(selected)) {
          g.setColor(g.getColor().brighter());
        }
        g.setStroke(new BasicStroke(16, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(r.draw());
        text(g, r.text(), r.label(), grade >= 0 ? Theme.TEXT_DARK : LABEL, 11f);
      } else {
        int grade = sheet.grade(r.text());
        boolean on = grade >= 0;
        g.setColor(on ? Theme.grade(grade) : CAPTION);
        if (r.key().equals(hover)) {
          g.setColor(g.getColor().brighter());
        }
        g.setStroke(new BasicStroke(on ? 2.6f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(r.draw());
        if (on) {
          String t = sheet.get(r.text(), "grade");
          Point2D p = r.label();
          boolean side = r.text().endsWith("med") || r.text().endsWith("lat");
          double tx = side ? (p.getX() < 200 ? p.getX() + 6 : p.getX() - 14) : p.getX() + 10;
          double ty = side ? p.getY() + 22 : p.getY() + 14;
          text(g, t, new Point2D.Double(tx, ty), Theme.grade(grade), 11f);
        }
      }
    }
    caption(g, "Medial meniscus", 110, 300);
    caption(g, "Lateral meniscus", 236, 300);
    caption(g, "Arrows: extrusion", 150, 322);
  }

  private void paintSoft(Graphics2D g) {
    caption(g, "KNEE · FROM THE SIDE · ANTERIOR LEFT", 18, 26);
    g.setColor(new Color(0x2A2A2D));
    g.fill(SvgPath.parse("M120 40c-30 40-50 100-44 150s24 110 40 140h170c14-50 30-110 26-160s-20-100-40-130z"));
    Color fluid = new Color(61, 110, 158, 140);
    Color fluidLine = new Color(90, 162, 232, 150);
    Shape eff = SvgPath.parse("M150 64c30-8 60 2 66 22l-6 52c-20 16-50 16-64 0z");
    g.setColor(fluid);
    g.fill(eff);
    g.setColor(fluidLine);
    g.draw(eff);
    bone(g, SvgPath.parse("M196 40h52l6 104c36 10 42 64 6 82-26 12-70 10-88-8-18-20-14-62 12-72z"));
    bone(g, new Ellipse2D.Double(113, 110, 30, 80));
    bone(g, SvgPath.parse("M150 246c34-10 110-10 140 0l-12 84H164z"));
    bone(g, new Ellipse2D.Double(286, 250, 28, 24));
    g.setColor(new Color(0xB9A57F));
    g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(SvgPath.parse("M126 190c6 24 18 44 32 58"));
    g.setColor(new Color(232, 210, 122, 190));
    g.fill(SvgPath.parse("M140 196c14-4 30 6 36 26 4 14-6 22-20 20-12-2-22-24-16-46z"));
    g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.setColor(new Color(0xC26B6B));
    g.draw(SvgPath.parse("M226 238l-22-36"));
    g.setColor(new Color(0x9E7BC4));
    g.draw(SvgPath.parse("M236 236l14-38"));
    g.setStroke(new BasicStroke(1f));
    Shape pop = new Ellipse2D.Double(284, 178, 32, 44);
    g.setColor(fluid);
    g.fill(pop);
    g.setColor(fluidLine);
    g.draw(pop);
    for (Region r : regions) {
      Ellipse2D c = (Ellipse2D) r.shape();
      String itemKey = firstItem(r.key());
      boolean entered = itemKey != null && sheet.isEntered(itemKey);
      int grade = entered ? sheet.grade(itemKey) : -2;
      if (r.key().equals(selected)) {
        g.setColor(Theme.alpha(Theme.ACCENT, 0.45));
        g.fill(new Ellipse2D.Double(c.getX() - 5, c.getY() - 5, c.getWidth() + 10, c.getHeight() + 10));
      }
      Color fill = !entered ? new Color(0x48484A) : grade < 0 ? CAPTION : Theme.grade(grade);
      if (r.key().equals(hover)) {
        fill = fill.brighter();
      }
      g.setColor(fill);
      g.fill(c);
      g.setColor(Theme.PANEL);
      g.setStroke(new BasicStroke(2f));
      g.draw(c);
      text(g, r.text(), new Point2D.Double(c.getCenterX(), c.getCenterY()),
          entered ? Theme.TEXT_DARK : Theme.TEXT, 10.5f, true);
    }
  }

  private static String firstItem(String locationKey) {
    List<Moaks.Item> items = Moaks.itemsAt(locationKey);
    return items.isEmpty() ? null : items.get(0).id();
  }

  private void highlight(Graphics2D g) {
    for (Region r : regions) {
      boolean sel = r.key().equals(selected);
      boolean hov = r.key().equals(hover);
      if (!sel && !hov) {
        continue;
      }
      g.setColor(sel ? Theme.ACCENT : Theme.alpha(Color.WHITE, 0.85));
      g.setStroke(new BasicStroke(sel ? 3f : 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g.draw(r.shape());
    }
  }

  /** The score pill next to the selected subregion, as in the mockup ("2.1 · 2"). */
  private void badge(Graphics2D g) {
    if (selected == null || layer == Layer.OSTEOPHYTES) {
      return;
    }
    Region r = null;
    for (Region x : regions) {
      if (x.key().equals(selected) && x.kind() == Kind.SURFACE) {
        r = x;
      }
    }
    if (r == null) {
      return;
    }
    List<String> parts = new ArrayList<>();
    int grade = -1;
    if (sheet.isEntered("cart." + selected)) {
      parts.add(sheet.get("cart." + selected, "score"));
      grade = Math.max(grade, sheet.grade("cart." + selected));
    }
    if (sheet.isEntered("bml." + selected)) {
      parts.add("BML " + sheet.get("bml." + selected, "size"));
      grade = Math.max(grade, sheet.grade("bml." + selected));
    }
    if (parts.isEmpty()) {
      return;
    }
    String t = String.join(" · ", parts);
    g.setFont(Theme.font(11f, true));
    FontMetrics fm = g.getFontMetrics();
    double w = fm.stringWidth(t) + 16;
    Rectangle2D b = r.shape().getBounds2D();
    boolean medial = b.getCenterX() < 170;
    double x = medial ? Math.max(2, b.getX() - w - 6) : Math.min(vw - w - 2, b.getMaxX() + 6);
    double y = b.getCenterY() - 11;
    g.setColor(Theme.grade(grade));
    g.fill(new RoundRectangle2D.Double(x, y, w, 22, 22, 22));
    g.setColor(Theme.TEXT_DARK);
    g.drawString(t, (float) (x + 8), (float) (y + 11 + (fm.getAscent() - fm.getDescent()) / 2.0));
  }

  private void marker(Graphics2D g, Ellipse2D c, int grade, boolean entered) {
    String key = null;
    for (Region r : regions) {
      if (r.shape() == c) {
        key = r.key();
      }
    }
    boolean sel = key != null && key.equals(selected);
    boolean hov = key != null && key.equals(hover);
    if (sel) {
      g.setColor(Theme.alpha(Theme.ACCENT, 0.45));
      g.fill(new Ellipse2D.Double(c.getX() - 4, c.getY() - 4, c.getWidth() + 8, c.getHeight() + 8));
    }
    Color fill = entered ? Theme.grade(Math.max(grade, 0)) : new Color(0x48484A);
    g.setColor(hov ? fill.brighter() : fill);
    g.fill(c);
    g.setColor(Theme.PANEL);
    g.setStroke(new BasicStroke(2f));
    g.draw(c);
    if (entered) {
      text(g, String.valueOf(Math.max(grade, 0)), new Point2D.Double(c.getCenterX(), c.getCenterY()),
          Theme.TEXT_DARK, 10.5f, true);
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
    // soft drop shadow
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

  private static void text(Graphics2D g, String s, Point2D at, Color c, float size) {
    g.setFont(Theme.font(size, true));
    g.setColor(c);
    g.drawString(s, (float) at.getX(), (float) at.getY());
  }

  private static void text(Graphics2D g, String s, Point2D center, Color c, float size, boolean centred) {
    g.setFont(Theme.font(size, true));
    FontMetrics fm = g.getFontMetrics();
    g.setColor(c);
    g.drawString(s, (float) (center.getX() - fm.stringWidth(s) / 2.0),
        (float) (center.getY() + (fm.getAscent() - fm.getDescent()) / 2.0));
  }
}
