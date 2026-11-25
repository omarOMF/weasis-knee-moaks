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
    s.assignDot(d.id(), "MFc");
    s.toggleFlag("men.MM-PH", "morph", "horizontal");
    s.toggleFlag("men.MM-PH", "morph", "partial_mac");
    s.set("ext.LM-lat", "grade", "1");
    s.set("syn.effusion", "grade", "2");
    s.set("syn.hoffa", "grade", "1");
    s.markNormal("lig.ACL");
    s.markNormal("lig.PCL");
    s.set("peri.POP", "present", "present");
    s.set("ost.OstFCM", "grade", "2");

    MoaksPanel panel = new MoaksPanel(ctl);
    ctl.select("MFc");
    render(panel, 400, 1180, out.resolve("panel-bone.png").toFile());
    ctl.select("MM-PH");
    render(panel, 400, 1050, out.resolve("panel-menisci.png").toFile());
    ctl.select("EFF");
    render(panel, 400, 1200, out.resolve("panel-soft.png").toFile());
    ctl.select("OstFCM");
    render(panel, 400, 1000, out.resolve("panel-osteophytes.png").toFile());

    JComponent where = PreviewAccess.where(ctl);
    render(where, 330, where.getPreferredSize().height, out.resolve("popup-where.png").toFile());
    JComponent scores = PreviewAccess.scores(ctl, "MFc");
    render(scores, 330, scores.getPreferredSize().height, out.resolve("popup-scores.png").toFile());
    System.out.println("Wrote previews to " + out.toAbsolutePath());
  }

  static void render(JComponent c, int w, int h, File file) throws Exception {
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

  private static void layout(Component c) {
    if (c instanceof Container ct) {
      ct.doLayout();
      for (Component ch : ct.getComponents()) {
        layout(ch);
      }
    }
  }
}
