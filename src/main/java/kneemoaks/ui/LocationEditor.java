package kneemoaks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import kneemoaks.model.Dot;
import kneemoaks.model.Moaks;
import kneemoaks.model.Moaks.Category;
import kneemoaks.model.Moaks.Field;
import kneemoaks.model.Moaks.Item;
import kneemoaks.model.Moaks.Option;
import kneemoaks.model.ScoreSheet;
import kneemoaks.ui.Widgets.ButtonStyle;
import kneemoaks.ui.Widgets.Chip;
import kneemoaks.ui.Widgets.Pill;
import kneemoaks.ui.Widgets.Segmented;

/**
 * The scoring card of one location: every MOAKS item scored there (for MFc: cartilage and bone
 * marrow lesion). Used in the side panel and in the pop-up next to a dot.
 */
public final class LocationEditor extends JPanel {

  private final MoaksController ctl;
  private final ScoreSheet sheet;
  private final String location;
  private final boolean inPopup;
  private final List<Runnable> refreshers = new ArrayList<>();
  private final JPanel dotRow = new JPanel();
  private final Runnable listener = this::refresh;
  private final StringBuilder typed = new StringBuilder();
  private final Timer typedReset = new Timer(900, e -> typed.setLength(0));

  public LocationEditor(MoaksController ctl, String location, boolean inPopup) {
    this.ctl = ctl;
    this.sheet = ctl.sheet();
    this.location = location;
    this.inPopup = inPopup;
    typedReset.setRepeats(false);
    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    build();
    refresh();
    installTyping();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    sheet.addListener(listener);
  }

  @Override
  public void removeNotify() {
    sheet.removeListener(listener);
    super.removeNotify();
  }

  private void build() {
    Moaks.Location loc = Moaks.location(location);
    JLabel code = Widgets.label(shortCode(location), 15f, true, Theme.TEXT);
    JLabel name = Widgets.label(loc == null ? "" : loc.name(), 13f, false, Theme.TEXT_2);
    if (!inPopup) {
      JPanel head = Widgets.row(8, code, name, Box.createHorizontalGlue());
      add(left(head));
      add(Box.createVerticalStrut(10));
    }

    for (Item item : Moaks.itemsAt(location)) {
      addItem(item);
      add(Box.createVerticalStrut(12));
    }

    if (!inPopup) {
      dotRow.setOpaque(false);
      dotRow.setLayout(new BoxLayout(dotRow, BoxLayout.X_AXIS));
      add(left(dotRow));
    }
  }

  private void addItem(Item item) {
    String title = switch (item.category()) {
      case CARTILAGE -> "Cartilage · area.full-thickness";
      case BML -> "Bone marrow lesion";
      case MENISCUS -> "Morphology · select all that apply";
      case EXTRUSION -> item.name().replace("Medial meniscus, ", "MM ").replace("Lateral meniscus, ", "LM ");
      case OSTEOPHYTE -> "Osteophyte size";
      case SYNOVITIS -> item.name();
      default -> item.name();
    };
    String legend = switch (item.category()) {
      case EXTRUSION -> "0 <2 mm · 1 2-2.9 · 2 3-4.9 · 3 ≥5 mm";
      case OSTEOPHYTE -> "0 none · 1 small · 2 medium · 3 large";
      case SYNOVITIS -> item.id().equals("syn.effusion")
          ? "0 normal · 1 small · 2 medium · 3 large"
          : "0 normal · 1 mild · 2 moderate · 3 severe";
      default -> null;
    };
    JLabel sec = Widgets.section(title);
    Pill clear = new Pill("Clear", ButtonStyle.LINK);
    clear.setFont(Theme.font(11f, true));
    clear.setToolTipText("Forget this item; it is saved as normal at Finish");
    clear.addActionListener(e -> sheet.clear(item.id()));
    clear.setPreferredSize(new Dimension(56, 22));
    clear.setMaximumSize(new Dimension(56, 22));
    add(left(Widgets.row(6, sec, Box.createHorizontalGlue(), clear)));
    refreshers.add(() -> clear.setVisible(sheet.isEntered(item.id())));
    add(Box.createVerticalStrut(4));

    if (item.category() == Category.CARTILAGE) {
      add(left(cartilageGrid(item)));
      return;
    }
    for (Field f : item.fields()) {
      if (f.multi()) {
        add(left(flagChips(item, f)));
      } else if (item.fields().size() > 1 || item.category() == Category.LIGAMENT) {
        JLabel l = Widgets.label(f.label(), 12.5f, false, new Color(0xE5E5EA));
        Segmented s = segmented(item, f);
        JPanel r = new JPanel(new BorderLayout(10, 0));
        r.setOpaque(false);
        r.add(l, BorderLayout.CENTER);
        r.add(s, BorderLayout.EAST);
        add(left(r));
        add(Box.createVerticalStrut(6));
      } else {
        add(left(segmentedFull(item, f)));
        if (legend != null) {
          add(Box.createVerticalStrut(4));
          add(left(Widgets.label(legend, 11f, false, Theme.TEXT_3)));
        }
      }
    }
  }

  private JComponent cartilageGrid(Item item) {
    Field f = item.field("score");
    JPanel grid = new JPanel(new GridLayout(2, 5, 6, 6));
    grid.setOpaque(false);
    for (Option o : f.options()) {
      Chip c = new Chip(o.label(), false);
      c.setToolTipText(o.meaning());
      c.getAccessibleContext().setAccessibleName("Cartilage " + o.label() + ", " + o.meaning());
      c.addActionListener(e -> sheet.set(item.id(), f.id(), o.code()));
      grid.add(c);
      refreshers.add(() -> {
        boolean entered = sheet.isEntered(item.id());
        boolean on = entered && sheet.get(item.id(), f.id()).equals(o.code());
        c.setOn(on, Theme.grade(o.grade()), Theme.TEXT_DARK);
        c.setGhost(!entered && o.code().equals(f.normal()));
      });
    }
    return grid;
  }

  private JComponent flagChips(Item item, Field f) {
    JPanel p = new JPanel(new Widgets.WrapLayout(6, 6));
    p.setOpaque(false);
    for (Option o : f.options()) {
      Chip c = new Chip(o.label(), true);
      c.setToolTipText(o.meaning());
      c.addActionListener(e -> sheet.toggleFlag(item.id(), f.id(), o.code()));
      p.add(c);
      refreshers.add(() -> {
        boolean on = ScoreSheet.splitFlags(sheet.get(item.id(), f.id())).contains(o.code());
        c.setOn(on, Theme.grade(o.grade()), Theme.TEXT_DARK);
      });
    }
    Chip none = new Chip("Normal", true);
    none.setToolTipText("Looked at: no finding");
    none.addActionListener(e -> sheet.set(item.id(), f.id(), ""));
    p.add(none);
    refreshers.add(() -> {
      boolean on = sheet.isEntered(item.id()) && sheet.get(item.id(), f.id()).isEmpty();
      none.setOn(on, Theme.grade(0), Theme.TEXT_DARK);
      none.setGhost(!sheet.isEntered(item.id()));
    });
    return p;
  }

  private Segmented segmented(Item item, Field f) {
    List<String> tips = new ArrayList<>();
    for (Option o : f.options()) {
      tips.add(o.label() + ": " + o.meaning());
    }
    Segmented s = new Segmented(Widgets.codes(f.options()), tips, false);
    s.getAccessibleContext().setAccessibleName(item.name() + " " + f.label());
    s.onSelect(i -> sheet.set(item.id(), f.id(), f.options().get(i).code()));
    Dimension d = s.getPreferredSize();
    int w = Math.max(d.width, f.options().size() == 4 ? 156 : d.width);
    s.setPreferredSize(new Dimension(w, d.height));
    s.setMaximumSize(new Dimension(w, d.height));
    refreshers.add(() -> {
      boolean entered = sheet.isEntered(item.id());
      s.setSelected(entered ? indexOf(f, sheet.get(item.id(), f.id())) : -1);
      s.setGhost(indexOf(f, f.normal()));
    });
    return s;
  }

  private JComponent segmentedFull(Item item, Field f) {
    Segmented s = segmented(item, f);
    s.setPreferredSize(new Dimension(10, s.getPreferredSize().height));
    s.setMaximumSize(new Dimension(Integer.MAX_VALUE, s.getPreferredSize().height));
    return s;
  }

  private static int indexOf(Field f, String code) {
    for (int i = 0; i < f.options().size(); i++) {
      if (f.options().get(i).code().equals(code)) {
        return i;
      }
    }
    return -1;
  }

  /** Typing "21" sets cartilage 2.1, as in the earlier versions. */
  private void installTyping() {
    Item cart = Moaks.item("cart." + location);
    if (cart == null) {
      return;
    }
    InputMap im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    for (int d = 0; d <= 3; d++) {
      String key = "moaks-digit-" + d;
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + d, 0), key);
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + d, 0), key);
      final int digit = d;
      getActionMap().put(key, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          typed.append(digit);
          typedReset.restart();
          if (typed.length() == 2) {
            String code = Moaks.parseCartilage(typed.toString());
            typed.setLength(0);
            if (code != null) {
              sheet.set(cart.id(), "score", code);
            }
          }
        }
      });
    }
  }

  public void refresh() {
    for (Runnable r : refreshers) {
      r.run();
    }
    if (!inPopup) {
      dotRow.removeAll();
      List<Dot> dots = sheet.dotsAt(location);
      boolean armedHere = location.equals(ctl.armed());
      Pill add = new Pill(armedHere ? "Click the image to place the dot…" : "Add dot", ButtonStyle.TINTED)
          .tint(Theme.ACCENT);
      add.addActionListener(e -> {
        if (armedHere) {
          ctl.setDotMode(false);
        } else {
          ctl.arm(location);
        }
      });
      dotRow.add(add);
      int grade = -1;
      for (Item i : Moaks.itemsAt(location)) {
        grade = Math.max(grade, sheet.grade(i.id()));
      }
      Color c = grade >= 0 ? Theme.grade(grade) : Theme.ACCENT;
      int n = 0;
      for (Dot d : dots) {
        n++;
        String where = ctl.describe(d);
        String text = "Dot" + (dots.size() > 1 ? " " + n : "") + (where.isEmpty() ? "" : " · " + where) + " · Go to";
        Pill go = new Pill(text, ButtonStyle.TINTED).tint(c).dot(c);
        go.addActionListener(e -> ctl.goTo(d));
        Pill del = new Pill("×", ButtonStyle.LINK);
        del.setToolTipText("Remove this dot");
        del.getAccessibleContext().setAccessibleName("Remove dot " + n);
        del.setPreferredSize(new Dimension(28, 30));
        del.addActionListener(e -> sheet.removeDot(d.id()));
        dotRow.add(Box.createHorizontalStrut(6));
        dotRow.add(go);
        dotRow.add(del);
      }
      dotRow.revalidate();
      dotRow.repaint();
    }
  }

  static String shortCode(String location) {
    return location.replace("-", " ").replace("MM B", "MM body").replace("LM B", "LM body");
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    if (!(c instanceof Segmented)) {
      c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 200));
    }
    return c;
  }
}
