package kneemoaks.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import kneemoaks.model.Moaks;
import kneemoaks.model.ScoreSheet;
import kneemoaks.model.StudyInfo;
import kneemoaks.ui.Widgets.ButtonStyle;
import kneemoaks.ui.Widgets.Card;
import kneemoaks.ui.Widgets.Pill;
import kneemoaks.ui.Widgets.ProgressRing;

/**
 * The Knee MOAKS side panel: the eight structures (and the whole knee), the drawing of the
 * selected one, and everything to score under it.
 */
public final class MoaksPanel extends JPanel {

  private static final String[] PARTS = {"Patella", "Trochlea", "Femur", "Tibia"};
  private static final String[] KEYS = {"PAT", "TRO", "FEM", "TIB"};

  private final MoaksController ctl;
  private final ScoreSheet sheet;

  private final JLabel kneeBadge = Widgets.label("", 11f, true, Theme.ACCENT);
  private final JLabel studyLine = Widgets.label("", 11.5f, false, Theme.TEXT_3);
  private final JComboBox<String> visit =
      new JComboBox<>(new String[] {"Baseline", "Month 12", "Month 24", "Month 48"});
  private final JTextField reader = new JTextField(5);
  private final Pill dotButton = new Pill("MOAKS dot", ButtonStyle.SECONDARY);
  private final Pill showDots = new Pill("Hide dots", ButtonStyle.LINK);
  private final List<StructureButton> buttons = new ArrayList<>();
  private final KneeArt art;
  private final JPanel editorHolder = new JPanel(new BorderLayout());
  private final ProgressRing ring = new ProgressRing();
  private final JLabel count = Widgets.label("", 12.5f, true, Theme.TEXT);
  private final JLabel status = Widgets.label("", 11f, false, Theme.TEXT_3);
  private final JLabel savedLine = Widgets.label("", 11f, false, Theme.TEXT_3);
  private final Pill undo = new Pill("Undo", ButtonStyle.LINK);
  private final JLabel emptyNote =
      Widgets.label("Open a knee MRI in the viewer to start.", 12.5f, false, Theme.TEXT_2);

  private String shownState = "";
  private boolean updating;

  public MoaksPanel(MoaksController ctl) {
    super(new BorderLayout());
    this.ctl = ctl;
    this.sheet = ctl.sheet();
    setBackground(Theme.PANEL);
    setOpaque(true);
    art = new KneeArt(sheet);
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
    kneeBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
    kneeBadge.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    kneeBadge.setToolTipText("Click to switch between right and left knee");
    kneeBadge.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ctl.setKneeSide("Right".equals(sheet.kneeSide()) ? "Left" : "Right");
      }
    });
    showDots.setToolTipText("Show or hide the dots on the images");
    showDots.addActionListener(e -> ctl.setDotsVisible(!ctl.dotsVisible()));
    dotButton.setToolTipText("Click the image to drop a dot, then pick the structure");
    dotButton.addActionListener(e -> ctl.setDotMode(!ctl.dotMode()));
    body.add(left(Widgets.row(8, title, new Badge(kneeBadge), Box.createHorizontalGlue(), dotButton)));
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
    body.add(left(Widgets.row(6, Widgets.label("Visit", 12f, false, Theme.TEXT_3), visit,
        Box.createHorizontalStrut(8), Widgets.label("Reader", 12f, false, Theme.TEXT_3), reader,
        Box.createHorizontalGlue())));
    body.add(Box.createVerticalStrut(14));

    // The eight structures, as on the dot card, plus the whole knee
    JPanel grid = new JPanel(new GridLayout(PARTS.length + 1, 2, 6, 6));
    grid.setOpaque(false);
    grid.add(head("Medial"));
    grid.add(head("Lateral"));
    for (int i = 0; i < PARTS.length; i++) {
      for (String side : new String[] {"M", "L"}) {
        StructureButton b = new StructureButton(KEYS[i] + "-" + side, PARTS[i]);
        buttons.add(b);
        grid.add(b);
      }
    }
    grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 5 * 50));
    body.add(left(grid));
    body.add(Box.createVerticalStrut(6));
    StructureButton whole = new StructureButton(Moaks.WHOLE_KNEE, "Whole knee · effusion, ligaments, cysts");
    buttons.add(whole);
    whole.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    body.add(left(whole));
    body.add(Box.createVerticalStrut(10));

    JPanel artRow = new JPanel(new BorderLayout());
    artRow.setOpaque(false);
    artRow.add(art, BorderLayout.CENTER);
    body.add(left(artRow));
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

  private static JLabel head(String s) {
    JLabel l = Widgets.label(s.toUpperCase(), 10.5f, true, Theme.TEXT_3);
    l.setHorizontalAlignment(SwingConstants.CENTER);
    return l;
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
    for (JLabel l : new JLabel[] {count, status, savedLine}) {
      l.setAlignmentX(Component.LEFT_ALIGNMENT);
      text.add(l);
    }
    f.add(ring, BorderLayout.WEST);
    f.add(text, BorderLayout.CENTER);
    undo.addActionListener(e -> sheet.undo());
    Pill folder = new Pill("Folder…", ButtonStyle.LINK);
    folder.addActionListener(e -> chooseFolder());
    Pill finish = new Pill("Finish knee", ButtonStyle.PRIMARY);
    finish.setToolTipText("Save the knee; anything you did not score is saved as normal");
    finish.addActionListener(e -> ctl.finish());
    JPanel buttonsRow = Widgets.row(2, undo, showDots, folder, Box.createHorizontalGlue(), finish);
    buttonsRow.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    f.add(buttonsRow, BorderLayout.SOUTH);
    return f;
  }

  private void chooseFolder() {
    JFileChooser fc = new JFileChooser(ctl.store().folder().toFile());
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    fc.setDialogTitle("Folder for the MOAKS CSV file");
    if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      Path p = fc.getSelectedFile().toPath();
      ctl.setFolder(p);
      ctl.saveNow(false);
    }
  }

  // ---- refresh ----

  private void refresh() {
    updating = true;
    try {
      StudyInfo st = sheet.study();
      boolean has = !st.isEmpty();
      kneeBadge.setText(sheet.kneeSide().isEmpty() ? "Knee?" : sheet.kneeSide() + " knee");
      studyLine.setText(has
          ? String.join(" · ", nonEmpty(st.patientId(), formatDate(st.studyDate()), st.seriesDescription()))
          : "No study open");
      if (!sheet.visit().equals(String.valueOf(visit.getSelectedItem()))) {
        visit.setSelectedItem(sheet.visit());
      }
      if (!reader.isFocusOwner()) {
        reader.setText(sheet.reader());
      }
      emptyNote.setVisible(!has);
      boolean on = ctl.dotMode();
      Moaks.Structure armed = Moaks.structure(ctl.armed());
      dotButton.setText(on ? (armed != null ? "Click: " + armed.name() : "Click the image…") : "MOAKS dot");
      dotButton.setActive(on);
      dotButton.revalidate();
      showDots.setText(ctl.dotsVisible() ? "Hide dots" : "Show dots");

      String sel = ctl.selected();
      art.setStructure(sel);
      art.getParent().setVisible(sel != null && !Moaks.WHOLE_KNEE.equals(sel));
      String state = sel + "|" + st.studyKey();
      if (!state.equals(shownState)) {
        shownState = state;
        rebuildEditor(sel);
      }
      refreshCounts();
      status.setText(has ? "Anything not scored is saved as normal" : " ");
      savedLine.setText((has ? ctl.saveStatus() + " · " : "") + "Knee MOAKS " + MoaksController.buildLabel());
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
    count.setText(n + " of " + total + " scored");
    undo.setEnabled(sheet.canUndo());
    for (StructureButton b : buttons) {
      b.repaint();
    }
  }

  private void rebuildEditor(String sel) {
    editorHolder.removeAll();
    if (sel != null && Moaks.structure(sel) != null) {
      Card c = new Card(new BorderLayout(), Theme.CARD, 22, 14);
      c.add(new StructureEditor(ctl, sel), BorderLayout.CENTER);
      editorHolder.add(c, BorderLayout.CENTER);
    } else if (!sheet.study().isEmpty()) {
      editorHolder.add(Widgets.label(
          "<html>Pick a structure above, or press <b>MOAKS dot</b> and click the image.</html>",
          12.5f, false, Theme.TEXT_2), BorderLayout.CENTER);
    }
    editorHolder.revalidate();
  }

  // ---- the structure buttons ----

  /** A structure in the grid: name, how much is scored, worst score as a coloured dot. */
  private final class StructureButton extends JButton {
    private final String key;

    StructureButton(String key, String text) {
      super(text);
      this.key = key;
      setContentAreaFilled(false);
      setBorderPainted(false);
      setFocusPainted(false);
      setOpaque(false);
      setRolloverEnabled(true);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setFont(Theme.font(13f, true));
      Moaks.Structure st = Moaks.structure(key);
      getAccessibleContext().setAccessibleName(st == null ? text : st.name());
      addActionListener(e -> ctl.select(key));
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(140, 44);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
      float w = getWidth();
      float h = getHeight();
      boolean sel = key.equals(ctl.selected());
      RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14);
      g2.setColor(sel ? Theme.alpha(Theme.ACCENT, 0.22) : getModel().isRollover() ? Theme.RAISED : Theme.CARD);
      g2.fill(r);
      if (sel || isFocusOwner()) {
        g2.setColor(Theme.ACCENT);
        g2.setStroke(new BasicStroke(sel ? 1.5f : 2f));
        g2.draw(r);
      }
      int grade = sheet.structureGrade(key);
      int done = sheet.structureEntered(key);
      Moaks.Structure st = Moaks.structure(key);
      int total = st == null ? 0 : st.itemIds().size();
      int dots = sheet.dotsAt(key).size();
      // status dot
      Ellipse2D pip = new Ellipse2D.Float(10, h / 2 - 5, 10, 10);
      if (done > 0) {
        g2.setColor(grade >= 0 ? Theme.grade(grade) : Theme.NEUTRAL_FINDING);
        g2.fill(pip);
      } else {
        g2.setColor(Theme.CHIP_LINE);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(pip);
      }
      g2.setFont(getFont());
      FontMetrics fm = g2.getFontMetrics();
      g2.setColor(Theme.TEXT);
      g2.drawString(getText(), 28, h / 2f - 2);
      g2.setFont(Theme.font(10.5f, false));
      g2.setColor(Theme.TEXT_3);
      String sub = done + " of " + total + (dots > 0 ? " · " + dots + (dots == 1 ? " dot" : " dots") : "");
      g2.drawString(sub, 28, h / 2f + fm.getAscent() - 2);
      g2.dispose();
    }
  }

  // ---- helpers ----

  private static String formatDate(String dicom) {
    if (dicom != null && dicom.matches("\\d{8}")) {
      return dicom.substring(0, 4) + "-" + dicom.substring(4, 6) + "-" + dicom.substring(6);
    }
    return dicom == null ? "" : dicom;
  }

  private static List<String> nonEmpty(String... s) {
    List<String> l = new ArrayList<>();
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
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = Theme.smooth(g);
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
