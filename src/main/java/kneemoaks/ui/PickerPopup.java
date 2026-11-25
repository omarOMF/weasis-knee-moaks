package kneemoaks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import kneemoaks.model.Dot;
import kneemoaks.model.Moaks;
import kneemoaks.ui.Widgets.ButtonStyle;
import kneemoaks.ui.Widgets.Card;
import kneemoaks.ui.Widgets.Chip;
import kneemoaks.ui.Widgets.Pill;

/**
 * The card that opens next to a new dot: "Where is this?" (type a code or tap one), then the
 * scores of that location right there.
 */
public final class PickerPopup {

  private static final List<String> GROUPS =
      List.of("FEMUR", "TIBIA", "PATELLA", "MENISCUS", "SOFT TISSUE", "OSTEOPHYTE");

  private final MoaksController ctl;
  private final String dotId;
  private JDialog dialog;
  private final Card card = new Card(new BorderLayout(), new Color(36, 36, 38), 18, 14);
  private final JPanel body = new JPanel();
  private boolean closing;
  private String scoresFor;

  PickerPopup(MoaksController ctl, String dotId) {
    this.ctl = ctl;
    this.dotId = dotId;
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
    card.add(body, BorderLayout.CENTER);
    showWhere("");
  }

  // ---- page 1: where is this? ----

  private JTextField search;

  private void showWhere(String text) {
    scoresFor = null;
    body.removeAll();
    JLabel title = Widgets.label("Where is this?", 15f, true, Theme.TEXT);
    title.setIcon(dotIcon(Theme.ACCENT));
    title.setIconTextGap(8);
    Pill close = new Pill("×", ButtonStyle.SECONDARY);
    close.setToolTipText("Cancel and remove the dot (Esc)");
    close.getAccessibleContext().setAccessibleName("Cancel and remove the dot");
    close.setPreferredSize(new Dimension(30, 26));
    close.setMaximumSize(new Dimension(30, 26));
    close.addActionListener(e -> close(true));
    body.add(left(Widgets.row(8, title, Box.createHorizontalGlue(), close)));
    body.add(Box.createVerticalStrut(10));

    search = new JTextField(text);
    search.setFont(Theme.font(13f, false));
    search.setForeground(Theme.TEXT);
    search.setCaretColor(Theme.TEXT);
    search.setBackground(Theme.PANEL);
    search.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.ACCENT, 2, true),
        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    search.putClientProperty("JTextField.placeholderText", "Type a code: MFc, MTp, MM PH…");
    search.getAccessibleContext().setAccessibleName("Type a subregion code");
    search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    body.add(left(search));
    body.add(Box.createVerticalStrut(10));

    JPanel results = new JPanel();
    results.setOpaque(false);
    results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
    body.add(left(results));
    fillResults(results, text);
    search.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        update();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        update();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        update();
      }

      private void update() {
        fillResults(results, search.getText());
        repack();
      }
    });
    search.addActionListener(e -> {
      List<Moaks.Location> hits = Moaks.search(search.getText());
      if (!hits.isEmpty()) {
        choose(hits.get(0).key());
      }
    });

    body.add(Box.createVerticalStrut(8));
    body.add(left(hint("Type to filter    ↵ choose    Esc cancel")));
    refreshUi();
    SwingUtilities.invokeLater(() -> search.requestFocusInWindow());
  }

  private void fillResults(JPanel results, String text) {
    results.removeAll();
    Map<String, JPanel> groups = new LinkedHashMap<>();
    List<Moaks.Location> hits = Moaks.search(text);
    for (String g : GROUPS) {
      for (Moaks.Location l : hits) {
        if (!l.group().equals(g)) {
          continue;
        }
        JPanel chips = groups.computeIfAbsent(g, k -> {
          JPanel p = new JPanel(new Widgets.WrapLayout(6, 4));
          p.setOpaque(false);
          return p;
        });
        String label = l.group().equals("OSTEOPHYTE")
            ? ostLabel(l)
            : l.group().equals("SOFT TISSUE") ? softLabel(l) : LocationEditor.shortCode(l.key());
        Chip c = new Chip(label, false);
        c.setToolTipText(l.name());
        c.getAccessibleContext().setAccessibleName(l.name());
        c.addActionListener(e -> choose(l.key()));
        chips.add(c);
      }
    }
    if (groups.isEmpty()) {
      results.add(left(Widgets.label("No match. Try MFc, MTp, MM PH or effusion.", 12f, false, Theme.TEXT_3)));
    }
    groups.forEach((g, chips) -> {
      results.add(left(Widgets.section(g)));
      results.add(left(chips));
      results.add(Box.createVerticalStrut(4));
    });
    results.revalidate();
  }

  private static String softLabel(Moaks.Location l) {
    return switch (l.key()) {
      case "EFF" -> "Effusion";
      case "HOF" -> "Hoffa";
      case "PT" -> "Patellar tendon";
      case "PES" -> "Pes anserine";
      case "POP" -> "Popliteal cyst";
      case "IPB" -> "Infrapatellar";
      case "PPB" -> "Prepatellar";
      case "GTF" -> "Ganglion tib-fib";
      case "GCR" -> "Ganglion cruciate";
      case "GOT" -> "Ganglion other";
      case "LB" -> "Loose body";
      default -> l.key();
    };
  }

  private static String ostLabel(Moaks.Location l) {
    return l.name().replace("Osteophyte, ", "")
        .replace("central femur", "femur central")
        .replace("posterior femur", "femur post.")
        .replace(" medial", " M").replace(" lateral", " L")
        .replace(" superior", " sup.").replace(" inferior", " inf.")
        .replace("trochlea", "Trochlea").replace("femur", "Femur").replace("patella", "Patella")
        .replace("tibia", "Tibia");
  }

  private void choose(String location) {
    ctl.sheet().assignDot(dotId, location);
    showScores(location);
  }

  // ---- page 2: score it there ----

  void showScores(String location) {
    scoresFor = location;
    body.removeAll();
    Moaks.Location loc = Moaks.location(location);
    JLabel title = Widgets.label(LocationEditor.shortCode(location), 15f, true, Theme.TEXT);
    int g = -1;
    for (Moaks.Item i : Moaks.itemsAt(location)) {
      g = Math.max(g, ctl.sheet().grade(i.id()));
    }
    title.setIcon(dotIcon(g >= 0 ? Theme.grade(g) : Theme.ACCENT));
    title.setIconTextGap(8);
    Pill change = new Pill("Change", ButtonStyle.LINK);
    change.addActionListener(e -> showWhere(""));
    JLabel name = Widgets.label(loc == null ? "" : loc.name(), 12f, false, Theme.TEXT_2);
    Pill delete = new Pill("Delete dot", ButtonStyle.TINTED).tint(Theme.grade(3));
    delete.setToolTipText("Remove this dot from the images (Delete key)");
    delete.addActionListener(e -> deleteDot());
    body.add(left(Widgets.row(8, title, Box.createHorizontalGlue(), change, delete)));
    body.add(left(name));
    body.add(Box.createVerticalStrut(10));
    LocationEditor editor = new LocationEditor(ctl, location, true);
    body.add(left(editor));

    Pill open = new Pill("Open in panel", ButtonStyle.SECONDARY);
    open.addActionListener(e -> {
      ctl.select(location);
      close(false);
    });
    Pill done = new Pill("Done ↵", ButtonStyle.PRIMARY);
    done.addActionListener(e -> close(false));
    JLabel tip = hint(Moaks.item("cart." + location) != null ? "Type 21 for 2.1" : "");
    body.add(Box.createVerticalStrut(4));
    body.add(left(Widgets.row(8, tip, Box.createHorizontalGlue(), open, done)));
    refreshUi();
    if (dialog != null) {
      dialog.getRootPane().setDefaultButton(done);
      SwingUtilities.invokeLater(editor::requestFocusInWindow);
      editor.setFocusable(true);
    }
  }

  // ---- window ----

  void showNear(Component view, Point screen) {
    Window owner = SwingUtilities.getWindowAncestor(view);
    dialog = new JDialog(owner);
    dialog.setUndecorated(true);
    dialog.setFocusableWindowState(true);
    try {
      dialog.setBackground(new Color(0, 0, 0, 0));
    } catch (UnsupportedOperationException e) {
      // no translucent windows on this platform: square corners
    }
    JPanel root = new JPanel(new BorderLayout());
    root.setOpaque(false);
    root.add(card);
    dialog.setContentPane(root);
    JRootPane rp = dialog.getRootPane();
    rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "moaks-close");
    rp.getActionMap().put("moaks-close", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        close(true);
      }
    });
    for (int key : new int[] {KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE}) {
      rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
          .put(KeyStroke.getKeyStroke(key, 0), "moaks-delete");
    }
    rp.getActionMap().put("moaks-delete", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (scoresFor != null) {
          deleteDot();
        }
      }
    });
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowDeactivated(WindowEvent e) {
        close(false);
      }
    });
    if (scoresFor != null) {
      showScores(scoresFor);
    }
    fitWidth();
    dialog.pack();
    Rectangle screenBounds = screenOf(view);
    int x = screen.x + 24;
    int y = screen.y - 60;
    if (x + dialog.getWidth() > screenBounds.getMaxX()) {
      x = screen.x - 24 - dialog.getWidth();
    }
    y = (int) Math.max(screenBounds.getY() + 8, Math.min(y, screenBounds.getMaxY() - dialog.getHeight() - 8));
    dialog.setLocation(x, y);
    dialog.setVisible(true);
  }

  private static Rectangle screenOf(Component c) {
    GraphicsConfiguration gc = c.getGraphicsConfiguration();
    return gc == null ? new Rectangle(0, 0, 1920, 1080) : gc.getBounds();
  }

  private void refreshUi() {
    fitWidth();
    body.revalidate();
    body.repaint();
    repack();
  }

  /** The card is 330 px wide; the wrapping chip rows need that width to work out their height. */
  private void fitWidth() {
    body.setSize(new Dimension(302, 10));
    body.doLayout();
    card.setPreferredSize(null);
    int h = body.getPreferredSize().height;
    card.setPreferredSize(new Dimension(330, h + 28));
  }

  private void repack() {
    fitWidth();
    if (dialog != null && dialog.isVisible()) {
      Point at = dialog.getLocation();
      dialog.pack();
      dialog.setLocation(at);
    }
  }

  private void deleteDot() {
    ctl.sheet().removeDot(dotId);
    close(true);
  }

  /** Closes the card. A dot that never got a location is removed. */
  void close(boolean cancel) {
    if (closing) {
      return;
    }
    closing = true;
    Dot d = ctl.sheet().dot(dotId);
    if (d != null && (d.location() == null)) {
      ctl.sheet().removeDot(dotId);
    }
    if (dialog != null) {
      dialog.dispose();
    }
    ctl.popupClosed(this);
  }

  private static JLabel hint(String s) {
    return Widgets.label(s, 11f, false, Theme.TEXT_3);
  }

  private static javax.swing.Icon dotIcon(Color c) {
    return new javax.swing.Icon() {
      @Override
      public void paintIcon(Component comp, java.awt.Graphics g, int x, int y) {
        java.awt.Graphics2D g2 = Theme.smooth(g);
        g2.setColor(c);
        g2.fillOval(x, y, 12, 12);
        g2.dispose();
      }

      @Override
      public int getIconWidth() {
        return 12;
      }

      @Override
      public int getIconHeight() {
        return 12;
      }
    };
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  /** For previews: the card's content without a window. */
  JComponent content() {
    fitWidth();
    return card;
  }
}
