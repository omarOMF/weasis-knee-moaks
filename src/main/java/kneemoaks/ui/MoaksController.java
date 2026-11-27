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
    /** Turns the "MOAKS marker" mouse mode on the image views on or off. */
    void setDotMode(boolean on);

    /** Scrolls every view to the slice of the dot and selects the view it was placed in. */
    void goTo(Dot dot);

    /** Repaints the views so dots and labels follow the scores. */
    void refreshViews();

    /** Where the series shown in a dot was placed, e.g. "Sag 14"; empty if unknown. */
    String describe(Dot dot);

    /** Brings the Knee MOAKS panel to the front, open on the right. */
    void showPanel();
  }

  private static final Preferences PREFS = Preferences.userRoot().node("kneemoaks");

  private final ScoreSheet sheet = new ScoreSheet();
  private ViewerHost host;
  private CsvStore store;
  private String selected;
  private String armed;
  private boolean dotMode;
  private boolean dotsVisible = true;
  private boolean outlinesVisible = true;
  private String selectedDot;
  private String saveStatus = "Not saved yet";
  private boolean dirty;
  private final Timer autosave;
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private StructurePopup popup;

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

  /** "build 12" from the stamp the GitHub build adds; "dev build" when built by hand. */
  public static String buildLabel() {
    try (java.io.InputStream in = MoaksController.class.getResourceAsStream("/kneemoaks/build.properties")) {
      if (in != null) {
        java.util.Properties p = new java.util.Properties();
        p.load(in);
        return "Outline build " + p.getProperty("build", "?");
      }
    } catch (IOException e) {
      // fall through
    }
    return "Outline dev build";
  }

  public static Path defaultFolder() {
    // In the home folder: macOS asks before an app may use Documents or Desktop.
    return Path.of(System.getProperty("user.home"), "KneeMOAKS");
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

  /** "Add marker" in the panel: the next click on an image becomes a marker of this structure. */
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
    // One dot per press of "MOAKS dot": the next click goes back to Weasis.
    setDotMode(false);
    closePopup();
    popup = new StructurePopup(this, dot.id());
    Point p = new Point(onView);
    SwingUtilities.convertPointToScreen(p, view);
    try {
      popup.showNear(view, p);
    } catch (RuntimeException e) {
      // If the card cannot open, do not leave an unnamed dot behind.
      sheet.removeDot(dot.id());
      popup = null;
      throw e;
    }
  }

  /** Removes a dot (from the card, a right-click on it, or the panel). */
  public void deleteDot(String dotId) {
    closePopup();
    if (dotId.equals(selectedDot)) {
      selectedDot = null;
    }
    sheet.removeDot(dotId);
  }

  public boolean dotsVisible() {
    return dotsVisible;
  }

  /** Smart outlines of the structures, drawn on the slice where each marker was placed. */
  public boolean outlinesVisible() {
    return outlinesVisible;
  }

  public void setOutlinesVisible(boolean on) {
    outlinesVisible = on;
    if (host != null) {
      host.refreshViews();
    }
    fire();
  }

  public void setDotsVisible(boolean on) {
    dotsVisible = on;
    if (host != null) {
      host.refreshViews();
    }
    fire();
  }

  /** The dot last clicked on the image; the Delete key removes it. */
  public String selectedDot() {
    return selectedDot;
  }

  /** Removes every dot of this knee read. */
  public void deleteAllDots() {
    closePopup();
    selectedDot = null;
    for (Dot d : sheet.dots()) {
      sheet.removeDot(d.id());
    }
  }

  /** A double-click on a dot: open its structure and bring the panel to the front. */
  public void openDot(Dot dot) {
    dotClicked(dot);
    if (host != null) {
      host.showPanel();
    }
  }

  /** A click on an existing dot: select it and open its structure in the side panel. */
  public void dotClicked(Dot dot) {
    selectedDot = dot.id();
    if (host != null) {
      host.refreshViews();
    }
    closePopup();
    if (dot.location() != null) {
      select(dot.location());
    }
  }

  void popupClosed(StructurePopup p) {
    if (popup == p) {
      popup = null;
    }
  }

  public void closePopup() {
    if (popup != null) {
      popup.close();
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

  /** Label drawn next to a dot on the image: its structure, e.g. "Medial femur". */
  public String dotLabel(Dot d) {
    if (d.location() == null) {
      return "Where is this?";
    }
    Moaks.Structure st = Moaks.structure(d.location());
    return st == null ? d.location() : st.name();
  }

  /** Short tag drawn next to a marker: MF, LT, MTr, PM... ("?" before a structure is picked). */
  public String markerTag(Dot d) {
    if (d.location() == null) {
      return "?";
    }
    return switch (d.location()) {
      case "PAT-M" -> "PM";
      case "PAT-L" -> "PL";
      case "TRO-M" -> "MTr";
      case "TRO-L" -> "LTr";
      case "FEM-M" -> "MF";
      case "FEM-L" -> "LF";
      case "TIB-M" -> "MT";
      case "TIB-L" -> "LT";
      default -> d.location();
    };
  }

  /** Colour grade of a dot: the worst finding under its structure; -1 if none. */
  public int dotGrade(Dot d) {
    return d.location() == null ? -1 : sheet.structureGrade(d.location());
  }

  // ---- saving ----

  public String saveStatus() {
    return saveStatus;
  }

  /** Saves; returns null when saved, else the reason it could not. */
  public String saveNow(boolean finished) {
    autosave.stop();
    if (sheet.study().isEmpty()) {
      saveStatus = "Nothing saved: no study is open in the viewer";
      fire();
      return "No knee MRI is open in the viewer, so there is nothing to save yet. "
          + "Open the study, click an image, then score it.";
    }
    String problem = null;
    try {
      store.save(sheet, finished);
    } catch (java.nio.file.FileSystemException e) {
      // macOS can refuse Weasis access to Documents: use a folder in the home folder instead.
      Path fallback = Path.of(System.getProperty("user.home"), "KneeMOAKS");
      if (!fallback.equals(store.folder())) {
        try {
          setFolder(fallback);
          store.save(sheet, finished);
        } catch (IOException e2) {
          problem = e2.getMessage();
        }
      } else {
        problem = e.getMessage();
      }
    } catch (IOException e) {
      problem = e.getMessage();
    }
    if (problem == null) {
      dirty = false;
      String time = java.time.LocalTime.now().withNano(0).toString().substring(0, 5);
      saveStatus = (finished ? "Finished and saved " : "Saved ") + time + " to " + shortPath(store.folder());
    } else {
      saveStatus = "Could not save: " + problem;
    }
    fire();
    return problem == null ? null : "Could not save the CSV: " + problem
        + "\nIf the file is open in Excel or Numbers, close it and press Finish knee again.";
  }

  static String shortPath(Path p) {
    String home = System.getProperty("user.home");
    String s = p.toString();
    return s.startsWith(home) ? "~" + s.substring(home.length()) : s;
  }

  /** Finish knee: untouched items are written as normal. Returns null on success, else why not. */
  public String finish() {
    return saveNow(true);
  }

  /** The CSV file with one row per scored item. */
  public Path csvFile() {
    return store.folder().resolve(CsvStore.LONG_FILE);
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
