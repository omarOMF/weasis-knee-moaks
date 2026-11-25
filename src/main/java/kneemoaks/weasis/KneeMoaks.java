package kneemoaks.weasis;

import kneemoaks.ui.MoaksController;

/** One controller for the whole Weasis session, shared by the side panel and the toolbar. */
final class KneeMoaks {

  private static MoaksController controller;
  private static WeasisHost host;
  private static int users;

  private KneeMoaks() {}

  static synchronized MoaksController acquire() {
    if (controller == null) {
      host = new WeasisHost();
      controller = new MoaksController(host);
      host.start(controller);
    }
    users++;
    return controller;
  }

  static synchronized void release() {
    users = Math.max(0, users - 1);
    if (users == 0 && controller != null) {
      controller.saveNow(false);
      controller.closePopup();
      host.stop();
      controller = null;
      host = null;
    }
  }
}
