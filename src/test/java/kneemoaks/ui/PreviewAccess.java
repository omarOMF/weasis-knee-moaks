package kneemoaks.ui;

import javax.swing.JComponent;
import kneemoaks.model.Dot;

/** Lets the preview draw the pop-up card without opening a window. */
public final class PreviewAccess {

  private PreviewAccess() {}

  public static JComponent where(MoaksController ctl) {
    Dot d = Dot.create(0, 0, 0, "", "", "", 0, 0);
    ctl.sheet().addDot(d);
    return new PickerPopup(ctl, d.id()).content();
  }

  public static JComponent scores(MoaksController ctl, String location) {
    Dot d = Dot.create(0, 0, 0, "", "", "", 0, 0);
    ctl.sheet().addDot(d);
    PickerPopup p = new PickerPopup(ctl, d.id());
    p.showScores(location);
    return p.content();
  }
}
