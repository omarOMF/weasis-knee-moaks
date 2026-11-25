package kneemoaks.ui;

import java.awt.Component;
import java.awt.Point;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import kneemoaks.model.CsvStore;
import kneemoaks.model.Dot;
import kneemoaks.model.Moaks;
import kneemoaks.model.ScoreSheet;
import kneemoaks.model.StudyInfo;

/**
 * Ties the panel, the pop-up and the image views together: what is selected, whether a dot is
 * waiting to be placed, autosave and Finish.
 */
public final class MoaksController {

  /** What the Weasis side offers; a stub in tests and previews. */
  public interface ViewerHost {
    /** Turns the "MOAKS dot" mouse mode on the image views on or off. */
    void setDotMode(boolean on);

    /** Scrolls every view to the slice of the dot and selects the view it was placed in. */
    void goTo(Dot dot);

    /** Repaints the views so dots and labels follow the scores. */
    void refreshViews();

    /** Where the series shown in a dot was placed, e.g. "Sag 14"; empty if unknown. */
    String describe(Dot dot);
  }

  private static final Preferences PREFS = Preferences.userRoot().node("kneemoaks");

  private final ScoreSheet sheet = new ScoreSheet();
  private ViewerHost host;
  private CsvStore store;
  private String selected;
  private String armed;
  private boolean dotMode;
  private String saveStatus = "Not saved yet";
  private boolean dirty;
  private final Timer autosave;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private PickerPopup popup;

  public MoaksController(ViewerHost host) {
    this.host = host;
    store = new CsvStore(Path.of(PREFS.get("folder", defaultFolder().toString())));
    autosave = new Timer(1200, e -> saveNow(false));
    autosave.setRepeats(false);
    sheet.addListener(() -> {
      dirty = true;
      autosave.restart();
      if (this.host != null) {
        this.host.refreshViews();
      }
      fire();
    });
  }

  public static Path defaultFolder() {
    return Path.of(System.getProperty("user.home"), "Documents", "KneeMOAKS");
  }

  public void setHost(ViewerHost host) {
    this.host = host;
  }

  public ScoreSheet sheet() {
    return sheet;
  }

  public CsvStore store() {
    return store;
  }

  public void setFolder(Path folder) {
    PREFS.put("folder", folder.toString());
    store = new CsvStore(folder);
    fire();
  }

  public String savedReader() {
    return PREFS.get("reader", "");
  }

  // ---- study ----

  /** Called when the reader opens another study; saves the current one and loads the new one. */
  public void openStudy(StudyInfo study) {
    if (study == null || study.studyKey().equals(sheet.study().studyKey()) && !sheet.study().isEmpty()) {
      return;
    }
    if (dirty) {
      saveNow(false);
    }
    closePopup();
    String side = study.guessKneeSide();
    sheet.reset(study, side.isEmpty() ? "Right" : side, sheet.visit().isEmpty() ? "Baseline" : sheet.visit(),
        savedReader());
    reload();
    selected = null;
    armed = null;
    dirty = false;
    fire();
  }

  /** Reloads the saved read after the knee, visit or reader changed. */
  public void reload() {
    try {
      boolean found = store.load(sheet);
      saveStatus = found ? "Opened your saved read" : "New read";
    } catch (IOException e) {
      saveStatus = "Could not read the CSV: " + e.getMessage();
    }
    dirty = false;
    fire();
  }

  public void setReader(String r) {
    if (dirty) {
      saveNow(false);
    }
    PREFS.put("reader", r == null ? "" : r.trim());
    sheet.setReader(r);
    reload();
  }

  public void setKneeSide(String side) {
    if (dirty) {
      saveNow(false);
    }
    sheet.setKneeSide(side);
    reload();
  }

  public void setVisit(String v) {
    if (dirty) {
      saveNow(false);
    }
    sheet.setVisit(v);
    reload();
  }

  // ---- selection and dots ----

  public String selected() {
    return selected;
  }

  public void select(String location) {
    selected = location;
    fire();
  }

  public String armed() {
    return armed;
  }

  public boolean dotMode() {
    return dotMode;
  }

  /** "Add dot" in the panel: the next click on an image becomes a dot of this location. */
  public void arm(String location) {
    armed = location;
    setDotMode(true);
  }

  public void setDotMode(boolean on) {
    dotMode = on;
    if (!on) {
      armed = null;
    }
    if (host != null) {
      host.setDotMode(on);
    }
    fire();
  }

  /**
   * A click on an image in dot mode. With a location armed from the panel, the dot goes there.
   * Otherwise the pop-up next to the click asks where it is.
   */
  public void dotPlaced(Dot dot, Component view, Point onView) {
    if (sheet.study().isEmpty()) {
      return;
    }
    sheet.addDot(dot);
    if (armed != null) {
      String loc = armed;
      sheet.assignDot(dot.id(), loc);
      armed = null;
      selected = loc;
      setDotMode(false);
      return;
    }
    closePopup();
    popup = new PickerPopup(this, dot.id());
    Point p = new Point(onView);
    SwingUtilities.convertPointToScreen(p, view);
    popup.showNear(view, p);
  }

  /** A click on an existing dot: reopen its scores in the pop-up. */
  public void dotClicked(Dot dot, Component view, Point onView) {
    closePopup();
    popup = new PickerPopup(this, dot.id());
    if (dot.location() != null) {
      popup.showScores(dot.location());
    }
    Point p = new Point(onView);
    SwingUtilities.convertPointToScreen(p, view);
    popup.showNear(view, p);
  }

  void popupClosed(PickerPopup p) {
    if (popup == p) {
      popup = null;
    }
  }

  public void closePopup() {
    if (popup != null) {
      popup.close(true);
      popup = null;
    }
  }

  public void goTo(Dot d) {
    if (host != null && d != null) {
      host.goTo(d);
    }
  }

  public String describe(Dot d) {
    return host == null ? "" : host.describe(d);
  }

  /** Label drawn next to a dot on the image. */
  public String dotLabel(Dot d) {
    if (d.location() == null) {
      return "Where is this?";
    }
    String s = sheet.summary(d.location());
    return s.isEmpty() ? d.location() : d.location() + " · " + s;
  }

  /** Colour grade of a dot: the worst finding at its location; -1 if none. */
  public int dotGrade(Dot d) {
    if (d.location() == null) {
      return -1;
    }
    int g = -1;
    for (Moaks.Item i : Moaks.itemsAt(d.location())) {
      g = Math.max(g, sheet.grade(i.id()));
    }
    return g;
  }

  // ---- saving ----

  public String saveStatus() {
    return saveStatus;
  }

  public void saveNow(boolean finished) {
    autosave.stop();
    if (sheet.study().isEmpty()) {
      return;
    }
    try {
      store.save(sheet, finished);
      dirty = false;
      String time = java.time.LocalTime.now().withNano(0).toString().substring(0, 5);
      saveStatus = (finished ? "Finished and saved " : "Saved ") + time;
    } catch (IOException e) {
      saveStatus = "Could not save: " + e.getMessage()
          + " (is the CSV open in another program?)";
    }
    fire();
  }

  /** Finish knee: untouched items are written as normal. */
  public void finish() {
    saveNow(true);
  }

  // ---- listeners ----

  public void addListener(Runnable r) {
    listeners.add(r);
  }

  private void fire() {
    for (Runnable r : listeners) {
      r.run();
    }
  }
}
