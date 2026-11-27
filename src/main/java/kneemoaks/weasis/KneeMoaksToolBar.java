package kneemoaks.weasis;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.Objects;
import javax.swing.JToggleButton;
import kneemoaks.ui.MoaksController;
import org.weasis.core.ui.util.WtoolBar;

/** A toolbar button for the "MOAKS marker" mode: click the image to place a marker. */
public final class KneeMoaksToolBar extends WtoolBar {

  private final MoaksController ctl;
  private final JToggleButton dot = new JToggleButton();

  public KneeMoaksToolBar() {
    super("Knee MOAKS Outline", 450);
    ctl = KneeMoaks.acquire();
    dot.setIcon(new FlatSVGIcon(
        Objects.requireNonNull(KneeMoaksToolBar.class.getResource("/icon/svg/moaks-marker.svg"))));
    dot.setText("MOAKS marker");
    dot.setToolTipText("MOAKS marker: click the image to place a marker, then pick the structure");
    dot.addActionListener(e -> ctl.setDotMode(dot.isSelected()));
    ctl.addListener(() -> {
      if (dot.isSelected() != ctl.dotMode()) {
        dot.setSelected(ctl.dotMode());
      }
    });
    add(dot);
  }

  void dispose() {
    KneeMoaks.release();
  }
}
