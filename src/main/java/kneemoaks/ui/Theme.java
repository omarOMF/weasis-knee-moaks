package kneemoaks.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.UIManager;

/** Colours and type of the panel: dark, macOS-like, same values as the approved mockup. */
public final class Theme {

  public static final Color WINDOW = new Color(0x141416);
  public static final Color PANEL = new Color(0x1C1C1E);
  public static final Color CARD = new Color(0x2C2C2E);
  public static final Color RAISED = new Color(0x3A3A3C);
  public static final Color SELECTED = new Color(0x636366);
  public static final Color LINE = new Color(0x3A3A3C);
  public static final Color CHIP_LINE = new Color(0x48484A);

  public static final Color TEXT = new Color(0xF5F5F7);
  public static final Color TEXT_2 = new Color(0xC7C7CC);
  public static final Color TEXT_3 = new Color(0xA1A1A6);
  public static final Color TEXT_DARK = new Color(0x1C1C1E);

  public static final Color ACCENT = new Color(0x0A84FF);
  public static final Color ACCENT_SOFT = new Color(10, 132, 255, 40);

  private static final Color[] GRADE = {
    new Color(0x30D158), new Color(0xFFD60A), new Color(0xFF9F0A), new Color(0xFF453A)
  };
  public static final Color NEUTRAL_FINDING = new Color(0x8E8E93);

  private Theme() {}

  /** Colour of a grade 0..3; anything else is neutral grey. */
  public static Color grade(int g) {
    return g >= 0 && g < GRADE.length ? GRADE[g] : NEUTRAL_FINDING;
  }

  /** Text colour that reads on a grade colour. */
  public static Color onGrade(int g) {
    return TEXT_DARK;
  }

  public static Color alpha(Color c, double a) {
    return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * a));
  }

  /** The system UI font (San Francisco on a Mac, via FlatLaf), at a size and weight. */
  public static Font font(float size, boolean bold) {
    Font base = UIManager.getFont("defaultFont");
    if (base == null) {
      base = UIManager.getFont("Label.font");
    }
    if (base == null) {
      base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }
    return base.deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
  }

  public static Graphics2D smooth(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    return g2;
  }
}
