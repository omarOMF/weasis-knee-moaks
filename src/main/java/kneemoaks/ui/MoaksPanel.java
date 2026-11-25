package kneemoaks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import kneemoaks.model.Moaks;
import kneemoaks.model.Moaks.Tab;
import kneemoaks.model.ScoreSheet;
import kneemoaks.model.StudyInfo;
import kneemoaks.ui.Widgets.ButtonStyle;
import kneemoaks.ui.Widgets.Card;
import kneemoaks.ui.Widgets.Pill;
import kneemoaks.ui.Widgets.ProgressRing;
import kneemoaks.ui.Widgets.Segmented;

/** The Knee MOAKS side panel: header, tabs, the knee illustration, the scoring card, footer. */
public final class MoaksPanel extends JPanel {

  private final MoaksController ctl;
  private final ScoreSheet sheet;

  private final JLabel kneeBadge = Widgets.label("", 11f, true, Theme.ACCENT);
  private final JLabel studyLine = Widgets.label("", 11.5f, false, Theme.TEXT_3);
  private final JComboBox<String> visit =
      new JComboBox<>(new String[] {"Baseline", "Month 12", "Month 24", "Month 48"});
  private final JTextField reader = new JTextField(5);
  private final Pill dotButton = new Pill("MOAKS dot", ButtonStyle.SECONDARY);
  private final Segmented tabs;
  private final Segmented layers;
  private final KneeArt art;
  private final JPanel softList = new JPanel();
  private final JPanel editorHolder = new JPanel(new BorderLayout());
  private final ProgressRing ring = new ProgressRing();
  private final JLabel count = Widgets.label("", 12.5f, true, Theme.TEXT);
  private final JLabel status = Widgets.label("", 11f, false, Theme.TEXT_3);
  private final JLabel savedLine = Widgets.label("", 11f, false, Theme.TEXT_3);
  private final Pill undo = new Pill("Undo", ButtonStyle.LINK);
  private final JLabel emptyNote =
      Widgets.label("Open a knee MRI in the viewer to start.", 12.5f, false, Theme.TEXT_2);

  private Tab tab = Tab.BONE;
  private String shownState = "";
  private boolean updating;

  public MoaksPanel(MoaksController ctl) {
    super(new BorderLayout());
    this.ctl = ctl;
    this.sheet = ctl.sheet();
    setBackground(Theme.PANEL);
    setOpaque(true);

    List<String> tabNames = List.of(Tab.BONE.label, Tab.MENISCI.label, Tab.SOFT.label);
    tabs = new Segmented(tabNames, null, true);
    tabs.setSelected(0);
    tabs.onSelect(i -> showTab(Tab.values()[i]));
    layers = new Segmented(List.of("Cartilage", "Marrow", "Osteophytes"), null, false);
    layers.setSelected(0);
    art = new KneeArt(sheet);
    layers.onSelect(i -> art.setLayer(KneeArt.Layer.values()[i]));
    art.onPick(ctl::select);

    add(buildBody(), BorderLayout.CENTER);
    add(buildFooter(), BorderLayout.SOUTH);

    ctl.addListener(this::refresh);
    sheet.addListener(this::refreshCounts);
    refresh();
  }

  private JComponent buildBody() {
    ScrollBody body = new ScrollBody();
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    body.setBackground(Theme.PANEL);
    body.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

    JLabel title = Widgets.label("Knee MOAKS", 17f, true, Theme.TEXT);
    kneeBadge.setOpaque(false);
    kneeBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    kneeBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    kneeBadge.setToolTipText("Click to switch between right and left knee");
    kneeBadge.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ctl.setKneeSide("Right".equals(sheet.kneeSide()) ? "Left" : "Right");
      }
    });
    JComponent badge = new Badge(kneeBadge);
    dotButton.setToolTipText("Click the image to drop a dot, then say where it is");
    dotButton.addActionListener(e -> ctl.setDotMode(!ctl.dotMode()));
    body.add(left(Widgets.row(8, title, badge, Box.createHorizontalGlue(), dotButton)));
    body.add(Box.createVerticalStrut(6));
    body.add(left(studyLine));
    body.add(Box.createVerticalStrut(8));

    visit.setEditable(true);
    visit.setFont(Theme.font(12f, false));
    visit.setMaximumSize(new Dimension(130, 28));
    visit.getAccessibleContext().setAccessibleName("Visit");
    visit.addActionListener(e -> {
      if (!updating) {
        Object v = visit.getSelectedItem();
        ctl.setVisit(v == null ? "" : v.toString());
      }
    });
    reader.setFont(Theme.font(12f, false));
    reader.setMaximumSize(new Dimension(70, 28));
    reader.getAccessibleContext().setAccessibleName("Reader initials");
    reader.setToolTipText("Your initials; saved with every row");
    reader.addActionListener(e -> ctl.setReader(reader.getText()));
    reader.addFocusListener(new java.awt.event.FocusAdapter() {
      @Override
      public void focusLost(java.awt.event.FocusEvent e) {
        if (!reader.getText().trim().equals(sheet.reader())) {
          ctl.setReader(reader.getText());
        }
      }
    });
    JLabel vl = Widgets.label("Visit", 12f, false, Theme.TEXT_3);
    JLabel rl = Widgets.label("Reader", 12f, false, Theme.TEXT_3);
    body.add(left(Widgets.row(6, vl, visit, Box.createHorizontalStrut(8), rl, reader, Box.createHorizontalGlue())));
    body.add(Box.createVerticalStrut(12));

    tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
    tabs.setPreferredSize(new Dimension(300, 30));
    body.add(left(tabs));
    body.add(Box.createVerticalStrut(10));
    layers.setMaximumSize(new Dimension(280, 26));
    layers.setPreferredSize(new Dimension(280, 26));
    body.add(left(layers));
    body.add(Box.createVerticalStrut(6));

    JPanel artRow = new JPanel(new BorderLayout());
    artRow.setOpaque(false);
    artRow.add(art, BorderLayout.CENTER);
    body.add(left(artRow));

    softList.setOpaque(false);
    softList.setLayout(new BoxLayout(softList, BoxLayout.Y_AXIS));
    body.add(left(softList));
    body.add(Box.createVerticalStrut(8));

    editorHolder.setOpaque(false);
    body.add(left(editorHolder));
    body.add(Box.createVerticalStrut(8));
    body.add(left(emptyNote));
    body.add(Box.createVerticalGlue());

    javax.swing.JScrollPane sp = new javax.swing.JScrollPane(body);
    sp.setBorder(BorderFactory.createEmptyBorder());
    sp.getViewport().setBackground(Theme.PANEL);
    sp.getVerticalScrollBar().setUnitIncrement(16);
    sp.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return sp;
  }

  private JComponent buildFooter() {
    JPanel f = new JPanel(new BorderLayout(10, 0));
    f.setBackground(Theme.PANEL);
    f.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE),
        BorderFactory.createEmptyBorder(10, 14, 12, 14)));
    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    count.setAlignmentX(Component.LEFT_ALIGNMENT);
    status.setAlignmentX(Component.LEFT_ALIGNMENT);
    savedLine.setAlignmentX(Component.LEFT_ALIGNMENT);
    text.add(count);
    text.add(status);
    text.add(savedLine);
    f.add(ring, BorderLayout.WEST);
    f.add(text, BorderLayout.CENTER);

    undo.addActionListener(e -> sheet.undo());
    Pill folder = new Pill("Folder…", ButtonStyle.LINK);
    folder.addActionListener(e -> chooseFolder());
    Pill finish = new Pill("Finish knee", ButtonStyle.PRIMARY);
    finish.setToolTipText("Save the knee; anything you did not touch is saved as normal");
    finish.addActionListener(e -> ctl.finish());
    JPanel buttons = Widgets.row(2, undo, folder, Box.createHorizontalGlue(), finish);
    buttons.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    f.add(buttons, BorderLayout.SOUTH);
    return f;
  }

  private void chooseFolder() {
    JFileChooser fc = new JFileChooser(ctl.store().folder().toFile());
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fc.setDialogTitle("Folder for the MOAKS CSV files");
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path p = fc.getSelectedFile().toPath();
      ctl.setFolder(p);
      ctl.saveNow(false);
    }
  }

  private void showTab(Tab t) {
    tab = t;
    tabs.setSelected(t.ordinal());
    art.setTab(t);
    layers.setVisible(t == Tab.BONE);
    rebuildSoftList();
    revalidate();
    repaint();
  }

  // ---- refresh ----

  private void refresh() {
    updating = true;
    try {
      StudyInfo st = sheet.study();
      boolean has = !st.isEmpty();
      kneeBadge.setText(sheet.kneeSide().isEmpty() ? "Knee?" : sheet.kneeSide() + " knee");
      String date = formatDate(st.studyDate());
      studyLine.setText(has
          ? String.join(" · ", nonEmpty(st.patientId(), date, st.seriesDescription()))
          : "No study open");
      if (!sheet.visit().equals(String.valueOf(visit.getSelectedItem()))) {
        visit.setSelectedItem(sheet.visit());
      }
      if (!reader.isFocusOwner()) {
        reader.setText(sheet.reader());
      }
      emptyNote.setVisible(!has);
      boolean on = ctl.dotMode();
      dotButton.setText(on ? (ctl.armed() != null ? "Placing " + LocationEditor.shortCode(ctl.armed()) + "…" : "Dot mode on") : "MOAKS dot");
      dotButton.setActive(on);
      dotButton.revalidate();

      String sel = ctl.selected();
      if (sel != null) {
        Moaks.Location loc = Moaks.location(sel);
        if (loc != null && loc.tab() != tab) {
          showTab(loc.tab());
        }
        if (loc != null && loc.group().equals("OSTEOPHYTE") && art.layer() != KneeArt.Layer.OSTEOPHYTES) {
          layers.setSelected(2);
          art.setLayer(KneeArt.Layer.OSTEOPHYTES);
        }
      }
      art.setSelected(sel);
      String state = sel + "|" + ctl.armed() + "|" + st.studyKey();
      if (!state.equals(shownState)) {
        shownState = state;
        rebuildEditor(sel);
      }
      rebuildSoftList();
      refreshCounts();
      status.setText(has ? "Untouched items save as normal" : " ");
      savedLine.setText(has ? ctl.saveStatus() : " ");
    } finally {
      updating = false;
    }
    revalidate();
    repaint();
  }

  private void refreshCounts() {
    int n = sheet.enteredCount();
    int total = ScoreSheet.totalCount();
    ring.setValue(n / (double) total);
    count.setText(n + " of " + total + " recorded");
    undo.setEnabled(sheet.canUndo());
    if (tab == Tab.SOFT) {
      rebuildSoftList();
    }
  }

  private void rebuildEditor(String sel) {
    editorHolder.removeAll();
    if (sel != null && Moaks.location(sel) != null) {
      Card c = new Card(new BorderLayout(), Theme.CARD, 22, 14);
      c.add(new LocationEditor(ctl, sel, false), BorderLayout.CENTER);
      editorHolder.add(c, BorderLayout.CENTER);
    } else if (!sheet.study().isEmpty()) {
      JLabel hint = Widgets.label(
          "<html>Click a subregion on the knee to score it, or press <b>MOAKS dot</b> and click the image.</html>",
          12.5f, false, Theme.TEXT_2);
      editorHolder.add(hint, BorderLayout.CENTER);
    }
    editorHolder.revalidate();
  }

  private void rebuildSoftList() {
    softList.removeAll();
    softList.setVisible(tab == Tab.SOFT);
    if (tab != Tab.SOFT) {
      return;
    }
    Card card = new Card(null, Theme.CARD, 22, 6);
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    List<String> keys = KneeArt.softOrder();
    for (int i = 0; i < keys.size(); i++) {
      String key = keys.get(i);
      card.add(softRow(key, i == keys.size() - 1));
    }
    softList.add(card);
    softList.revalidate();
  }

  private JComponent softRow(String key, boolean last) {
    Moaks.Location loc = Moaks.location(key);
    String item = Moaks.itemsAt(key).get(0).id();
    boolean entered = sheet.isEntered(item);
    int grade = entered ? sheet.grade(item) : -2;
    Color fill = !entered ? new Color(0x48484A) : grade < 0 ? Theme.NEUTRAL_FINDING : Theme.grade(grade);
    JLabel num = new JLabel(String.valueOf(KneeArt.pinNumber(key)), JLabel.CENTER) {
      @Override
      protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = Theme.smooth(g);
        g2.setColor(fill);
        g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    num.setFont(Theme.font(10.5f, true));
    num.setForeground(entered ? Theme.TEXT_DARK : Theme.TEXT);
    num.setPreferredSize(new Dimension(20, 20));
    num.setMaximumSize(new Dimension(20, 20));
    JLabel name = Widgets.label(loc.name(), 12.5f, false, new Color(0xE5E5EA));
    String v = entered ? sheet.summary(key) : "—";
    JLabel val = Widgets.label(v, 12.5f, true, entered && grade > 0 ? Theme.TEXT : Theme.TEXT_3);
    JPanel row = Widgets.row(10, num, name, Box.createHorizontalGlue(), val);
    boolean sel = key.equals(ctl.selected());
    row.setOpaque(sel);
    row.setBackground(Theme.alpha(Theme.ACCENT, 0.22));
    row.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(0, 0, last ? 0 : 1, 0, Theme.LINE),
        BorderFactory.createEmptyBorder(7, 8, 7, 8)));
    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    row.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ctl.select(key);
      }
    });
    row.getAccessibleContext().setAccessibleName(loc.name() + ", " + v);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    return row;
  }

  // ---- helpers ----

  private static String formatDate(String dicom) {
    if (dicom != null && dicom.matches("\\d{8}")) {
      return dicom.substring(0, 4) + "-" + dicom.substring(4, 6) + "-" + dicom.substring(6);
    }
    return dicom == null ? "" : dicom;
  }

  private static List<String> nonEmpty(String... s) {
    java.util.ArrayList<String> l = new java.util.ArrayList<>();
    for (String x : s) {
      if (x != null && !x.isBlank()) {
        l.add(x);
      }
    }
    return l;
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  /** Rounded tinted pill around the knee label. */
  private static final class Badge extends JPanel {
    Badge(JLabel l) {
      super(new BorderLayout());
      setOpaque(false);
      add(l);
      setMaximumSize(getPreferredSize());
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
      java.awt.Graphics2D g2 = Theme.smooth(g);
      g2.setColor(Theme.ACCENT_SOFT);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
      g2.dispose();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }

  /** Tracks the viewport width so content wraps instead of scrolling sideways. */
  private static final class ScrollBody extends JPanel implements Scrollable {
    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
      return 16;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
      return r.height - 32;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }
  }
}
