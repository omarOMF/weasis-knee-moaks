package kneemoaks;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.UIManager;
import kneemoaks.model.Dot;
import kneemoaks.model.StudyInfo;
import kneemoaks.ui.MoaksController;
import kneemoaks.ui.MoaksPanel;
import kneemoaks.ui.PreviewAccess;

/**
 * Draws the panel into PNG files without a screen, with sample scores like the mockup, so its
 * look can be checked: {@code java -Djava.awt.headless=true kneemoaks.Preview out-dir}.
 */
public final class Preview {

  public static void main(String[] args) throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      try {
        run(args);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
    System.exit(0);
  }

  static void run(String[] args) throws Exception {
    Path out = Path.of(args.length > 0 ? args[0] : "preview");
    Files.createDirectories(out);
    UIManager.put("TextField.background", new Color(0x2C2C2E));
    UIManager.put("TextField.foreground", new Color(0xF5F5F7));
    UIManager.put("ComboBox.background", new Color(0x2C2C2E));
    UIManager.put("ComboBox.foreground", new Color(0xF5F5F7));

    MoaksController ctl = new MoaksController(new MoaksController.ViewerHost() {
      @Override
      public void setDotMode(boolean on) {}

      @Override
      public void goTo(Dot dot) {}

      @Override
      public void refreshViews() {}

      @Override
      public String describe(Dot dot) {
        return "Sag 14";
      }

      @Override
      public void showPanel() {}
    });
    ctl.setFolder(Files.createTempDirectory("kneemoaks-preview"));
    ctl.openStudy(new StudyInfo("[Patient ID]", "", "20261120", "1.2.3", "1.2.3.4", "SAG PD FS RT", "R"));
    ctl.setReader("AB");
    var s = ctl.sheet();
    s.set("cart.MFc", "score", "2.1");
    s.set("bml.MFc", "size", "2");
    s.set("bml.MFc", "pct", "3");
    s.set("bml.MFc", "number", "1");
    s.set("cart.LFc", "score", "0.0");
    s.set("cart.PM", "score", "1.0");
    s.set("bml.MTp", "size", "1");
    s.set("cart.MTp", "score", "1.1");
    Dot d = Dot.create(0, 0, 0, "", "", "", 0, 0);
    s.addDot(d);
    s.assignDot(d.id(), "FEM-M");
    s.toggleFlag("men.MM-PH", "morph", "horizontal");
    s.toggleFlag("men.MM-PH", "morph", "partial_mac");
    s.set("men.MM-AH", "morph", "");
    s.set("ext.MM-med", "grade", "1");
    s.set("syn.effusion", "grade", "2");
    s.set("syn.hoffa", "grade", "1");
    s.markNormal("lig.ACL");
    s.set("peri.POP", "present", "present");
    s.set("ost.OstFCM", "grade", "2");

    MoaksPanel panel = new MoaksPanel(ctl);
    ctl.select("FEM-M");
    render(panel, 400, 1500, out.resolve("panel-femur.png").toFile());
    ctl.select("TIB-M");
    render(panel, 400, 1900, out.resolve("panel-tibia.png").toFile());
    ctl.select("WHOLE");
    render(panel, 400, 1500, out.resolve("panel-whole.png").toFile());
    ctl.select(null);
    render(panel, 400, 900, out.resolve("panel-start.png").toFile());

    JComponent card = PreviewAccess.dotCard(ctl);
    card.setSize(card.getPreferredSize());
    render(card, card.getPreferredSize().width, card.getPreferredSize().height,
        out.resolve("dot-card.png").toFile());
    System.out.println("Wrote previews to " + out.toAbsolutePath());
  }

  static void render(JComponent c, int w, int h, File file) throws Exception {
    invalidateAll(c);
    c.setSize(w, h);
    layout(c);
    layout(c);
    BufferedImage img = new BufferedImage(w * 2, h * 2, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = img.createGraphics();
    g.scale(2, 2);
    g.setColor(new Color(0x1C1C1E));
    g.fillRect(0, 0, w, h);
    c.printAll(g);
    g.dispose();
    ImageIO.write(img, "png", file);
  }

  private static void invalidateAll(Component c) {
    c.invalidate();
    if (c instanceof Container ct) {
      for (Component ch : ct.getComponents()) {
        invalidateAll(ch);
      }
    }
  }

  private static void layout(Component c) {
    if (c instanceof Container ct) {
      ct.doLayout();
      for (Component ch : ct.getComponents()) {
        layout(ch);
      }
    }
  }
}
