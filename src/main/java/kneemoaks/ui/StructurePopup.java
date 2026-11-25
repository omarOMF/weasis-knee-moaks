package kneemoaks.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import kneemoaks.model.Dot;
import kneemoaks.model.Moaks;
import kneemoaks.ui.Widgets.ButtonStyle;
import kneemoaks.ui.Widgets.Card;
import kneemoaks.ui.Widgets.Chip;
import kneemoaks.ui.Widgets.Pill;

/**
 * The small card next to a new dot: eight buttons, medial and lateral patella, trochlea, femur
 * and tibia. Picking one names the dot; the scoring happens later in the side panel.
 */
public final class StructurePopup {

  private static final String[] PARTS = {"Patella", "Trochlea", "Femur", "Tibia"};
  private static final String[] KEYS = {"PAT", "TRO", "FEM", "TIB"};

  private final MoaksController ctl;
  private final String dotId;
  private final Card card = new Card(new BorderLayout(), new Color(36, 36, 38), 18, 14);
  private JDialog dialog;
  private boolean closing;

  StructurePopup(MoaksController ctl, String dotId) {
    this.ctl = ctl;
    this.dotId = dotId;
    JPanel body = new JPanel();
    body.setOpaque(false);
    body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

    JLabel title = Widgets.label("Where is this?", 15f, true, Theme.TEXT);
    Pill close = new Pill("×", ButtonStyle.SECONDARY);
    close.setToolTipText("Cancel and remove the dot (Esc)");
    close.getAccessibleContext().setAccessibleName("Cancel and remove the dot");
    close.setPreferredSize(new Dimension(30, 26));
    close.setMaximumSize(new Dimension(30, 26));
    close.addActionListener(e -> close());
    body.add(left(Widgets.row(8, title, Box.createHorizontalGlue(), close)));
    body.add(Box.createVerticalStrut(10));

    JPanel grid = new JPanel(new GridLayout(PARTS.length + 1, 2, 8, 8));
    grid.setOpaque(false);
    grid.add(head("Medial"));
    grid.add(head("Lateral"));
    for (int i = 0; i < PARTS.length; i++) {
      for (String side : new String[] {"M", "L"}) {
        String key = KEYS[i] + "-" + side;
        Moaks.Structure st = Moaks.structure(key);
        Chip c = new Chip(PARTS[i], false);
        c.setFont(Theme.font(13.5f, true));
        c.getAccessibleContext().setAccessibleName(st.name());
        c.setToolTipText(st.name());
        c.setPreferredSize(new Dimension(130, 38));
        c.addActionListener(e -> choose(key));
        grid.add(c);
      }
    }
    body.add(left(grid));
    body.add(Box.createVerticalStrut(8));
    body.add(left(Widgets.label("Score it later in the Knee MOAKS panel.", 11f, false, Theme.TEXT_3)));
    card.add(body, BorderLayout.CENTER);
  }

  private static JLabel head(String s) {
    JLabel l = Widgets.label(s.toUpperCase(), 10.5f, true, Theme.TEXT_3);
    l.setHorizontalAlignment(SwingConstants.CENTER);
    return l;
  }

  private void choose(String key) {
    ctl.sheet().assignDot(dotId, key);
    ctl.select(key);
    close();
  }

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
        close();
      }
    });
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowDeactivated(WindowEvent e) {
        close();
      }
    });
    dialog.pack();
    Rectangle sb = screenOf(view);
    int x = screen.x + 24;
    int y = screen.y - dialog.getHeight() / 2;
    if (x + dialog.getWidth() > sb.getMaxX()) {
      x = screen.x - 24 - dialog.getWidth();
    }
    y = (int) Math.max(sb.getY() + 8, Math.min(y, sb.getMaxY() - dialog.getHeight() - 8));
    dialog.setLocation(x, y);
    dialog.setVisible(true);
  }

  private static Rectangle screenOf(Component c) {
    GraphicsConfiguration gc = c.getGraphicsConfiguration();
    return gc == null ? new Rectangle(0, 0, 1920, 1080) : gc.getBounds();
  }

  /** Closes the card; a dot that was not named is removed. */
  void close() {
    if (closing) {
      return;
    }
    closing = true;
    Dot d = ctl.sheet().dot(dotId);
    if (d != null && d.location() == null) {
      ctl.sheet().removeDot(dotId);
    }
    if (dialog != null) {
      dialog.dispose();
    }
    ctl.popupClosed(this);
  }

  private static JComponent left(JComponent c) {
    c.setAlignmentX(Component.LEFT_ALIGNMENT);
    return c;
  }

  /** For previews: the card without a window. */
  JComponent content() {
    return card;
  }
}
