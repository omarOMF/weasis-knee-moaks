package kneemoaks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
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
 * Everything scored under one structure, in one list: cartilage, bone marrow lesions,
 * osteophytes and, for the tibia, the meniscus. Details appear only when needed: a BML's
 * percentage and count once it has a size, a meniscus's findings once it is marked abnormal.
 */
public final class StructureEditor extends JPanel {

  private final MoaksController ctl;
  private final ScoreSheet sheet;
  private final Moaks.Structure structure;
  private final List<Runnable> refreshers = new ArrayList<>();
  private final JPanel dotRow = new JPanel();
  private final Set<String> openMenisci = new HashSet<>();
  private final Runnable listener = this::refresh;

  public StructureEditor(MoaksController ctl, String structureKey) {
    this.ctl = ctl;
    this.sheet = ctl.sheet();
    this.structure = Moaks.structure(structureKey);
    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    build();
    refresh();
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
    JLabel title = Widgets.label(structure.name(), 16f, true, Theme.TEXT);
    Pill normal = new Pill("Rest normal", ButtonStyle.SECONDARY);
    normal.setToolTipText("Mark everything not yet scored here as normal");
    normal.addActionListener(e -> sheet.markStructureNormal(structure.key()));
    add(left(Widgets.row(8, title, Box.createHorizontalGlue(), normal)));
    add(Box.createVerticalStrut(8));
    if (!Moaks.WHOLE_KNEE.equals(structure.key())) {
      dotRow.setOpaque(false);
      dotRow.setLayout(new BoxLayout(dotRow, BoxLayout.X_AXIS));
      add(left(dotRow));
      add(Box.createVerticalStrut(6));
    }

    Category last = null;
    for (String id : structure.itemIds()) {
      Item item = Moaks.item(id);
      if (item.category() != last) {
        last = item.category();
        add(Box.createVerticalStrut(8));
        add(left(Widgets.section(sectionTitle(item.category()))));
        String legend = legend(item);
        if (legend != null) {
          add(left(Widgets.label(legend, 10.5f, false, Theme.TEXT_3)));
        }
        add(Box.createVerticalStrut(4));
      }
      addItem(item);
    }
  }

  private static String sectionTitle(Category c) {
    return switch (c) {
      case CARTILAGE -> "Cartilage · area.full-thickness";
      case BML -> "Bone marrow lesions · size";
      case OSTEOPHYTE -> "Osteophytes";
      case MENISCUS -> "Meniscus";
      case EXTRUSION -> "Meniscal extrusion";
      case SYNOVITIS -> "Synovitis";
      case LIGAMENT -> "Ligaments and tendon";
      case PERIARTICULAR -> "Cysts and bursae";
      case LOOSE_BODY -> "Loose bodies";
    };
  }

  private static String legend(Item item) {
    return switch (item.category()) {
      case BML -> "0 none · 1 <33% · 2 33-66% · 3 >66% of the subregion";
      case OSTEOPHYTE -> "0 none · 1 small · 2 medium · 3 large";
      case EXTRUSION -> "0 <2 mm · 1 2-2.9 · 2 3-4.9 · 3 ≥5 mm";
      case SYNOVITIS -> "0 normal · 1 mild/small · 2 moderate/medium · 3 severe/large";
      default -> null;
    };
  }

  /** "MFc central", "Posterior horn", "Superior"... */
  private static String rowLabel(Item item) {
    Moaks.Location loc = Moaks.location(item.location());
    String name = loc == null ? item.name() : loc.name();
    return switch (item.category()) {
      case CARTILAGE, BML -> item.location() + "  " + after(name);
      case OSTEOPHYTE -> cap(after(name));
      case MENISCUS -> cap(after(name));
      case EXTRUSION -> cap(item.name().substring(item.name().indexOf(", ") + 2).replace(" extrusion", ""));
      default -> item.name();
    };
  }

  private static String after(String s) {
    int i = s.indexOf(", ");
    return i < 0 ? s : s.substring(i + 2);
  }

  private static String cap(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private void addItem(Item item) {
    switch (item.category()) {
      case CARTILAGE -> {
        Field f = item.field("score");
        add(left(Widgets.label(rowLabel(item), 12.5f, true, new Color(0xE5E5EA))));
        add(Box.createVerticalStrut(3));
        add(left(segmented(item, f, true)));
        add(Box.createVerticalStrut(8));
      }
      case BML -> {
        add(left(line(rowLabel(item), segmented(item, item.field("size"), true))));
        JPanel more = new JPanel();
        more.setOpaque(false);
        more.setLayout(new BoxLayout(more, BoxLayout.Y_AXIS));
        more.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 0));
        more.add(left(line("% that is lesion", segmented(item, item.field("pct"), false))));
        more.add(left(line("Number", segmented(item, item.field("number"), false))));
        add(left(more));
        refreshers.add(() -> more.setVisible(!sheet.get(item.id(), "size").equals("0")));
      }
      case MENISCUS -> addMeniscus(item);
      case LIGAMENT -> {
        if (item.fields().size() == 1) {
          add(left(line(item.name(), segmented(item, item.fields().get(0), true))));
        } else {
          add(left(Widgets.label(item.name(), 12.5f, true, new Color(0xE5E5EA))));
          for (Field f : item.fields()) {
            add(left(line("  " + f.label(), segmented(item, f, true))));
          }
        }
      }
      default -> add(left(line(rowLabel(item), segmented(item, item.fields().get(0), true))));
    }
  }

  private void addMeniscus(Item item) {
    Field f = item.field("morph");
    Segmented state = new Segmented(List.of("Normal", "Abnormal"), null, false);
    state.setSelectedColors(List.of(Theme.grade(0), Theme.grade(2)));
    state.setPreferredSize(new Dimension(150, 26));
    state.setMaximumSize(new Dimension(150, 26));
    state.onSelect(i -> {
      if (i == 0) {
        openMenisci.remove(item.id());
        sheet.set(item.id(), "morph", "");
      } else {
        openMenisci.add(item.id());
        refresh();
      }
    });
    add(left(line(rowLabel(item), state)));
    JPanel chips = new JPanel(new Widgets.WrapLayout(5, 4));
    chips.setOpaque(false);
    chips.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 0));
    for (Option o : f.options()) {
      Chip c = new Chip(o.label(), true);
      c.setToolTipText(o.meaning());
      c.addActionListener(e -> sheet.toggleFlag(item.id(), "morph", o.code()));
      chips.add(c);
      refreshers.add(() -> c.setOn(ScoreSheet.splitFlags(sheet.get(item.id(), "morph")).contains(o.code()),
          Theme.grade(o.grade()), Theme.TEXT_DARK));
    }
    add(left(chips));
    refreshers.add(() -> {
      boolean flags = !ScoreSheet.splitFlags(sheet.get(item.id(), "morph")).isEmpty();
      boolean open = flags || openMenisci.contains(item.id());
      chips.setVisible(open);
      state.setSelected(open ? 1 : sheet.isEntered(item.id()) ? 0 : -1);
      state.setGhost(0);
    });
  }

  private Segmented segmented(Item item, Field f, boolean colours) {
    List<String> tips = new ArrayList<>();
    List<Color> cols = new ArrayList<>();
    for (Option o : f.options()) {
      tips.add(o.label() + ": " + o.meaning());
      cols.add(o.grade() < 0 ? null : Theme.grade(o.grade()));
    }
    Segmented s = new Segmented(Widgets.codes(f.options()), tips, false);
    if (colours) {
      s.setSelectedColors(cols);
    }
    s.getAccessibleContext().setAccessibleName(item.name() + " " + f.label());
    s.onSelect(i -> sheet.set(item.id(), f.id(), f.options().get(i).code()));
    Dimension d = s.getPreferredSize();
    boolean wide = f.options().size() > 4; // cartilage codes: fill the row
    int w = f.options().size() == 4 ? 140 : wide ? 10 : Math.max(d.width, 150);
    s.setPreferredSize(new Dimension(w, wide ? 28 : 26));
    s.setMaximumSize(new Dimension(wide ? Integer.MAX_VALUE : w, wide ? 28 : 26));
    refreshers.add(() -> {
      boolean entered = sheet.isEntered(item.id());
      s.setSelected(entered ? indexOf(f, sheet.get(item.id(), f.id())) : -1);
      s.setGhost(indexOf(f, f.normal()));
    });
    return s;
  }

  private static JPanel line(String label, JComponent control) {
    JPanel p = new JPanel(new BorderLayout(8, 0));
    p.setOpaque(false);
    p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    p.add(Widgets.label(label, 12.5f, false, new Color(0xE5E5EA)), BorderLayout.CENTER);
    p.add(control, BorderLayout.EAST);
    return p;
  }

  private static int indexOf(Field f, String code) {
    for (int i = 0; i < f.options().size(); i++) {
      if (f.options().get(i).code().equals(code)) {
        return i;
      }
    }
    return -1;
  }

  public void refresh() {
    for (Runnable r : refreshers) {
      r.run();
    }
    if (!Moaks.WHOLE_KNEE.equals(structure.key())) {
      dotRow.removeAll();
      dotRow.setLayout(new BoxLayout(dotRow, BoxLayout.Y_AXIS));
      boolean armed = structure.key().equals(ctl.armed());
      Pill add = new Pill(armed ? "Click the image…" : "Add dot", ButtonStyle.TINTED).tint(Theme.ACCENT);
      add.setToolTipText("Place a dot for this structure on the image");
      add.addActionListener(e -> {
        if (armed) {
          ctl.setDotMode(false);
        } else {
          ctl.arm(structure.key());
        }
      });
      int g = sheet.structureGrade(structure.key());
      Color c = g >= 0 ? Theme.grade(g) : Theme.ACCENT;
      List<Dot> dots = sheet.dotsAt(structure.key());
      int n = 0;
      for (Dot d : dots) {
        n++;
        String where = ctl.describe(d);
        Pill go = new Pill((where.isEmpty() ? "Dot " + n : "Dot on " + where) + " · Go to", ButtonStyle.TINTED)
            .tint(c).dot(c);
        go.addActionListener(e -> ctl.goTo(d));
        Pill del = new Pill("Delete", ButtonStyle.TINTED).tint(Theme.grade(3));
        del.setToolTipText("Delete this dot");
        del.getAccessibleContext().setAccessibleName("Delete dot " + n);
        del.addActionListener(e -> ctl.deleteDot(d.id()));
        JPanel line = Widgets.row(6, go, del, Box.createHorizontalGlue());
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        dotRow.add(line);
        dotRow.add(Box.createVerticalStrut(6));
      }
      JPanel addLine = Widgets.row(6, add, Box.createHorizontalGlue());
      addLine.setAlignmentX(Component.LEFT_ALIGNMENT);
      dotRow.add(addLine);
      dotRow.revalidate();
      dotRow.repaint();
    }
    revalidate();
    repaint();
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    c.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(c.getMaximumSize().height, c.getPreferredSize().height)));
    return c;
  }
}
