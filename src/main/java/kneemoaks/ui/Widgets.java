package kneemoaks.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** The small custom-drawn controls of the panel: segmented controls, chips, buttons, cards. */
public final class Widgets {

  private Widgets() {}

  public static JLabel label(String text, float size, boolean bold, Color color) {
    JLabel l = new JLabel(text);
    l.setFont(Theme.font(size, bold));
    l.setForeground(color);
    return l;
  }

  /** Small caps section title, e.g. "BONE MARROW LESION". */
  public static JLabel section(String text) {
    JLabel l = label(text.toUpperCase(), 10.5f, true, Theme.TEXT_3);
    l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    return l;
  }

  // ------------------------------------------------------------------------------------------

  /** An Apple-style segmented control. Index -1 = nothing chosen; a ghost marks the default. */
  public static final class Segmented extends JComponent {
    private final List<String> labels;
    private final List<String> tips;
    private final boolean large;
    private int selected = -1;
    private int ghost = -1;
    private int hover = -1;
    private int focusIndex = 0;
    private IntConsumer onSelect = i -> {};
    private List<Color> selectedColors;

    public Segmented(List<String> labels, List<String> tips, boolean large) {
      this.labels = List.copyOf(labels);
      this.tips = tips == null ? List.of() : List.copyOf(tips);
      this.large = large;
      setFocusable(true);
      setOpaque(false);
      setFont(Theme.font(large ? 12.5f : 12f, true));
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      MouseAdapter m = new MouseAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          int i = indexAt(e.getX());
          if (i != hover) {
            hover = i;
            setToolTipText(i >= 0 && i < Segmented.this.tips.size() ? Segmented.this.tips.get(i) : null);
            repaint();
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          hover = -1;
          repaint();
        }

        @Override
        public void mousePressed(MouseEvent e) {
          int i = indexAt(e.getX());
          if (i >= 0) {
            choose(i);
          }
        }
      };
      addMouseListener(m);
      addMouseMotionListener(m);
      addKeyListener(new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent e) {
          switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT -> moveFocus(-1);
            case KeyEvent.VK_RIGHT -> moveFocus(1);
            case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> choose(focusIndex);
            default -> {
              return;
            }
          }
          e.consume();
        }
      });
      addFocusListener(new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
          focusIndex = Math.max(0, selected);
          repaint();
        }

        @Override
        public void focusLost(FocusEvent e) {
          repaint();
        }
      });
      getAccessibleContext().setAccessibleName(String.join(", ", labels));
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleJComponent() {
          @Override
          public javax.accessibility.AccessibleRole getAccessibleRole() {
            return javax.accessibility.AccessibleRole.RADIO_BUTTON;
          }
        };
      }
      return accessibleContext;
    }

    private void moveFocus(int d) {
      focusIndex = Math.floorMod(focusIndex + d, labels.size());
      repaint();
    }

    private void choose(int i) {
      selected = i;
      repaint();
      onSelect.accept(i);
    }

    public void onSelect(IntConsumer c) {
      onSelect = c;
    }

    public void setSelected(int i) {
      selected = i;
      repaint();
    }

    public int selected() {
      return selected;
    }

    public void setGhost(int i) {
      ghost = i;
      repaint();
    }

    /** Fills the chosen segment with its own colour (score colours) instead of grey. */
    public void setSelectedColors(List<Color> colors) {
      selectedColors = colors;
      repaint();
    }

    private int indexAt(int x) {
      int n = labels.size();
      int w = getWidth() - 4;
      if (n == 0 || w <= 0) {
        return -1;
      }
      int i = (x - 2) * n / w;
      return i >= 0 && i < n ? i : -1;
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(40, getPreferredSize().height);
    }

    @Override
    public Dimension getPreferredSize() {
      if (isPreferredSizeSet()) {
        return super.getPreferredSize();
      }
      FontMetrics fm = getFontMetrics(getFont());
      int seg = 0;
      for (String l : labels) {
        seg = Math.max(seg, fm.stringWidth(l) + (large ? 22 : 16));
      }
      return new Dimension(seg * labels.size() + 4, large ? 30 : 26);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      int n = labels.size();
      float w = getWidth();
      float h = getHeight();
      g2.setColor(large ? Theme.CARD : Theme.PANEL);
      g2.fill(new RoundRectangle2D.Float(0, 0, w, h, large ? 18 : 16, large ? 18 : 16));
      float segW = (w - 4) / n;
      for (int i = 0; i < n; i++) {
        float x = 2 + i * segW;
        RoundRectangle2D.Float r = new RoundRectangle2D.Float(x, 2, segW, h - 4, large ? 14 : 12, large ? 14 : 12);
        if (i == selected) {
          g2.setColor(selectedColors != null && i < selectedColors.size() && selectedColors.get(i) != null
              ? selectedColors.get(i) : Theme.SELECTED);
          g2.fill(r);
        } else if (i == hover) {
          g2.setColor(Theme.alpha(Theme.SELECTED, 0.35));
          g2.fill(r);
        }
        if (i == ghost && selected < 0) {
          g2.setColor(Theme.alpha(Theme.TEXT_3, 0.55));
          g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {3, 3}, 0));
          g2.draw(new RoundRectangle2D.Float(x + 1, 3, segW - 2, h - 6, 11, 11));
        }
        if (isFocusOwner() && i == focusIndex) {
          g2.setColor(Theme.ACCENT);
          g2.setStroke(new BasicStroke(2f));
          g2.draw(new RoundRectangle2D.Float(x + 1, 3, segW - 2, h - 6, 11, 11));
        }
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String s = labels.get(i);
        boolean colored = selectedColors != null && i < selectedColors.size() && selectedColors.get(i) != null;
        g2.setColor(i == selected ? (colored ? Theme.TEXT_DARK : Color.WHITE) : Theme.TEXT_2);
        g2.drawString(s, x + (segW - fm.stringWidth(s)) / 2f, (h + fm.getAscent() - fm.getDescent()) / 2f);
      }
      g2.dispose();
    }
  }

  // ------------------------------------------------------------------------------------------

  /** A rounded chip: tap to choose. Filled with its colour when on. */
  public static final class Chip extends JButton {
    private boolean on;
    private boolean ghost;
    private Color onColor = Theme.SELECTED;
    private Color onText = Color.WHITE;
    private final boolean pill;

    public Chip(String text, boolean pill) {
      super(text);
      this.pill = pill;
      setFont(Theme.font(12.5f, true));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setMargin(new Insets(0, 0, 0, 0));
    }

    public void setOn(boolean on, Color color, Color text) {
      this.on = on;
      this.onColor = color;
      this.onText = text;
      getAccessibleContext().setAccessibleDescription(on ? "selected" : null);
      repaint();
    }

    public void setGhost(boolean g) {
      ghost = g;
      repaint();
    }

    public boolean isOn() {
      return on;
    }

    @Override
    public Dimension getPreferredSize() {
      FontMetrics fm = getFontMetrics(getFont());
      return new Dimension(fm.stringWidth(getText()) + (pill ? 22 : 18), pill ? 28 : 30);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(34, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      float w = getWidth();
      float h = getHeight();
      float arc = pill ? h : 16;
      RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, arc, arc);
      if (on) {
        g2.setColor(onColor);
        g2.fill(r);
      } else {
        g2.setColor(getModel().isRollover() ? Theme.RAISED : (pill ? Theme.CARD : Theme.CARD));
        g2.fill(r);
        g2.setColor(ghost ? Theme.alpha(Theme.TEXT_3, 0.8f) : Theme.CHIP_LINE);
        if (ghost) {
          g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] {3, 3}, 0));
        }
        g2.draw(r);
      }
      if (isFocusOwner()) {
        g2.setColor(Theme.ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, arc, arc));
      }
      g2.setFont(getFont());
      FontMetrics fm = g2.getFontMetrics();
      g2.setColor(on ? onText : Theme.TEXT);
      String s = getText();
      g2.drawString(s, (w - fm.stringWidth(s)) / 2f, (h + fm.getAscent() - fm.getDescent()) / 2f);
      g2.dispose();
    }
  }

  // ------------------------------------------------------------------------------------------

  public enum ButtonStyle {
    PRIMARY,
    SECONDARY,
    LINK,
    TINTED
  }

  /** A rounded push button in one of the panel's styles. */
  public static final class Pill extends JButton {
    private final ButtonStyle style;
    private boolean active;
    private Color tint = Theme.ACCENT;
    private Color dotColor;

    public Pill(String text, ButtonStyle style) {
      super(text);
      this.style = style;
      setFont(Theme.font(12.5f, true));
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** Shows the button as the primary (blue) style while a mode is on. */
    public void setActive(boolean on) {
      active = on;
      repaint();
    }

    public Pill tint(Color c) {
      tint = c;
      repaint();
      return this;
    }

    /** Shows a small coloured dot before the text (used by the "Go to dot" button). */
    public Pill dot(Color c) {
      dotColor = c;
      revalidate();
      repaint();
      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      FontMetrics fm = getFontMetrics(getFont());
      int extra = dotColor == null ? 0 : 14;
      return new Dimension(fm.stringWidth(getText()) + 24 + extra, 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      float w = getWidth();
      float h = getHeight();
      boolean roll = getModel().isRollover() && isEnabled();
      boolean pressed = getModel().isPressed();
      Color bg;
      Color fg;
      switch (active ? ButtonStyle.PRIMARY : style) {
        case PRIMARY -> {
          bg = pressed ? Theme.ACCENT.darker() : roll ? new Color(0x409CFF) : Theme.ACCENT;
          fg = Color.WHITE;
        }
        case SECONDARY -> {
          bg = roll ? Theme.SELECTED : Theme.RAISED;
          fg = Theme.TEXT;
        }
        case TINTED -> {
          bg = Theme.alpha(tint, roll ? 0.28 : 0.18);
          fg = tint.brighter();
        }
        default -> {
          bg = roll ? Theme.alpha(Theme.ACCENT, 0.12) : null;
          fg = Theme.ACCENT;
        }
      }
      if (!isEnabled()) {
        fg = Theme.alpha(fg, 0.4);
        if (bg != null) {
          bg = Theme.alpha(bg, 0.4);
        }
      }
      RoundRectangle2D.Float r = new RoundRectangle2D.Float(0, 0, w, h, 16, 16);
      if (bg != null) {
        g2.setColor(bg);
        g2.fill(r);
      }
      if (isFocusOwner()) {
        g2.setColor(style == ButtonStyle.PRIMARY || active ? Color.WHITE : Theme.ACCENT);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 15, 15));
      }
      g2.setFont(getFont());
      FontMetrics fm = g2.getFontMetrics();
      String s = getText();
      float tw = fm.stringWidth(s) + (dotColor == null ? 0 : 14);
      float x = (w - tw) / 2f;
      if (dotColor != null) {
        g2.setColor(dotColor);
        g2.fill(new Ellipse2D.Float(x, h / 2f - 4, 8, 8));
        x += 14;
      }
      g2.setColor(fg);
      g2.drawString(s, x, (h + fm.getAscent() - fm.getDescent()) / 2f);
      g2.dispose();
    }
  }

  // ------------------------------------------------------------------------------------------

  /** A rounded panel background. */
  public static class Card extends JPanel {
    private final Color fill;
    private final int arc;

    public Card(LayoutManager layout, Color fill, int arc, int pad) {
      super(layout);
      this.fill = fill;
      this.arc = arc;
      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      g2.setColor(fill);
      g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
      g2.dispose();
    }
  }

  // ------------------------------------------------------------------------------------------

  /** Circular progress, like the activity rings. */
  public static final class ProgressRing extends JComponent {
    private double value;

    public ProgressRing() {
      setPreferredSize(new Dimension(34, 34));
      setOpaque(false);
    }

    public void setValue(double v) {
      value = Math.max(0, Math.min(1, v));
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      float s = Math.min(getWidth(), getHeight()) - 6;
      float x = (getWidth() - s) / 2f;
      float y = (getHeight() - s) / 2f;
      g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
      g2.setColor(Theme.RAISED);
      g2.draw(new Ellipse2D.Float(x, y, s, s));
      if (value > 0) {
        g2.setColor(value >= 1 ? Theme.grade(0) : Theme.ACCENT);
        g2.draw(new Arc2D.Float(x, y, s, s, 90, (float) (-360 * value), Arc2D.OPEN));
      }
      g2.dispose();
    }
  }

  // ------------------------------------------------------------------------------------------

  /** FlowLayout that wraps and reports the wrapped height, so chips flow onto new lines. */
  public static final class WrapLayout extends FlowLayout {
    public WrapLayout(int hgap, int vgap) {
      super(FlowLayout.LEADING, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
      return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
      Dimension d = layoutSize(target, false);
      d.width -= getHgap() + 1;
      return d;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
      synchronized (target.getTreeLock()) {
        int targetWidth = target.getSize().width;
        Container c = target;
        while (c.getSize().width == 0 && c.getParent() != null) {
          c = c.getParent();
        }
        targetWidth = c.getSize().width;
        if (targetWidth == 0) {
          targetWidth = Integer.MAX_VALUE;
        }
        Insets in = target.getInsets();
        int maxWidth = targetWidth - (in.left + in.right + getHgap() * 2);
        Dimension dim = new Dimension(0, 0);
        int rowW = 0;
        int rowH = 0;
        for (Component m : target.getComponents()) {
          if (!m.isVisible()) {
            continue;
          }
          Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
          if (rowW + d.width > maxWidth) {
            addRow(dim, rowW, rowH);
            rowW = 0;
            rowH = 0;
          }
          if (rowW != 0) {
            rowW += getHgap();
          }
          rowW += d.width;
          rowH = Math.max(rowH, d.height);
        }
        addRow(dim, rowW, rowH);
        dim.width += in.left + in.right + getHgap() * 2;
        dim.height += in.top + in.bottom + getVgap() * 2;
        Container scroll = SwingUtilities.getAncestorOfClass(javax.swing.JScrollPane.class, target);
        if (scroll != null && target.isValid()) {
          dim.width -= getHgap() + 1;
        }
        return dim;
      }
    }

    private void addRow(Dimension dim, int rowW, int rowH) {
      dim.width = Math.max(dim.width, rowW);
      if (dim.height > 0) {
        dim.height += getVgap();
      }
      dim.height += rowH;
    }
  }

  /** Lays the given components out left to right with a gap, vertically centred. */
  public static JPanel row(int gap, Component... parts) {
    JPanel p = new JPanel();
    p.setOpaque(false);
    p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.X_AXIS));
    for (int i = 0; i < parts.length; i++) {
      if (i > 0 && gap > 0) {
        p.add(javax.swing.Box.createHorizontalStrut(gap));
      }
      if (parts[i] instanceof JComponent jc) {
        jc.setAlignmentY(Component.CENTER_ALIGNMENT);
      }
      p.add(parts[i]);
    }
    return p;
  }

  public static List<String> codes(List<kneemoaks.model.Moaks.Option> opts) {
    List<String> l = new ArrayList<>();
    for (kneemoaks.model.Moaks.Option o : opts) {
      l.add(o.label());
    }
    return l;
  }

  static Font bold(float size) {
    return Theme.font(size, true);
  }
}
