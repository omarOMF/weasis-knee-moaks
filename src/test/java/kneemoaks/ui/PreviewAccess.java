package kneemoaks.ui;

import javax.swing.JComponent;
import kneemoaks.model.Dot;

/** Lets the preview draw the dot card without opening a window. */
public final class PreviewAccess {

  private PreviewAccess() {}

  public static JComponent dotCard(MoaksController ctl) {
    Dot d = Dot.create(0, 0, 0, "", "", "", 0, 0);
    ctl.sheet().addDot(d);
    return new StructurePopup(ctl, d.id()).content();
  }
}
