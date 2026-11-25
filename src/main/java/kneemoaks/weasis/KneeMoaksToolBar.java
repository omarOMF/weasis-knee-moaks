package kneemoaks.weasis;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.util.Objects;
import javax.swing.JToggleButton;
import kneemoaks.ui.MoaksController;
import org.weasis.core.ui.util.WtoolBar;

/** A toolbar button for the "MOAKS dot" mode: click the image to drop a dot. */
public final class KneeMoaksToolBar extends WtoolBar {

  private final MoaksController ctl;
  private final JToggleButton dot = new JToggleButton();

  public KneeMoaksToolBar() {
    super("Knee MOAKS", 450);
    ctl = KneeMoaks.acquire();
    dot.setIcon(new FlatSVGIcon(
        Objects.requireNonNull(KneeMoaksToolBar.class.getResource("/icon/svg/moaks-dot.svg"))));
    dot.setText("MOAKS dot");
    dot.setToolTipText("MOAKS dot: click the image to drop a dot, then say where it is");
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
