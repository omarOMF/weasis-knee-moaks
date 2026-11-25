package kneemoaks.weasis;

import bibliothek.gui.dock.common.CLocation;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Objects;
import kneemoaks.ui.MoaksController;
import kneemoaks.ui.MoaksPanel;
import org.weasis.core.ui.docking.PluginTool;

/** The "Knee MOAKS" panel in the right-hand tool area of the DICOM viewer. */
public final class KneeMoaksTool extends PluginTool {

  public static final String NAME = "Knee MOAKS";

  private final MoaksController ctl;

  public KneeMoaksTool(Type type) {
    super(NAME, type, 5);
    dockable.setTitleIcon(new FlatSVGIcon(
        Objects.requireNonNull(KneeMoaksTool.class.getResource("/icon/svg/kneemoaks.svg"))));
    setDockableWidth(380);
    ctl = KneeMoaks.acquire();
    setLayout(new BorderLayout());
    MoaksPanel panel = new MoaksPanel(ctl);
    panel.setPreferredSize(new Dimension(380, 900));
    add(panel, BorderLayout.CENTER);
  }

  @Override
  public Component getToolComponent() {
    return this;
  }

  @Override
  protected void changeToolWindowAnchor(CLocation clocation) {
    // The panel lays itself out vertically wherever it is docked.
  }

  void dispose() {
    KneeMoaks.release();
  }
}
