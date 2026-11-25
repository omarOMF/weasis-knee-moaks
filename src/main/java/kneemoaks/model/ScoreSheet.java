package kneemoaks.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import kneemoaks.model.Moaks.Field;
import kneemoaks.model.Moaks.Item;
import kneemoaks.model.Moaks.Option;

/**
 * The scores and dots of one knee read. Only what the reader entered is stored; anything else is
 * normal, and {@link #withNormalsFilled()} writes those normals out when the knee is finished.
 */
public final class ScoreSheet {

  private StudyInfo study = StudyInfo.NONE;
  private String kneeSide = "";
  private String visit = "Baseline";
  private String reader = "";

  private final Map<String, Map<String, String>> values = new LinkedHashMap<>();
  private final List<Dot> dots = new ArrayList<>();
  private final Deque<Snapshot> undo = new ArrayDeque<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  private record Snapshot(Map<String, Map<String, String>> values, List<Dot> dots) {}

  // ---- header ----

  public StudyInfo study() {
    return study;
  }

  public String kneeSide() {
    return kneeSide;
  }

  public String visit() {
    return visit;
  }

  public String reader() {
    return reader;
  }

  /** Starts a new read: clears scores, dots and undo. */
  public void reset(StudyInfo study, String kneeSide, String visit, String reader) {
    this.study = study == null ? StudyInfo.NONE : study;
    this.kneeSide = nz(kneeSide);
    this.visit = nz(visit);
    this.reader = nz(reader);
    values.clear();
    dots.clear();
    undo.clear();
    fire();
  }

  public void setKneeSide(String side) {
    kneeSide = nz(side);
    fire();
  }

  public void setVisit(String v) {
    visit = nz(v);
    fire();
  }

  public void setReader(String r) {
    reader = nz(r);
    fire();
  }

  /** The key that decides which saved rows belong to this read. */
  public String readKey() {
    return String.join(
        "|", study.studyKey(), kneeSide.toLowerCase(), visit.toLowerCase(), reader.toLowerCase());
  }

  // ---- scores ----

  public boolean isEntered(String itemId) {
    return values.containsKey(itemId);
  }

  /** The value of a field: what the reader entered, else the normal value. */
  public String get(String itemId, String fieldId) {
    Map<String, String> m = values.get(itemId);
    if (m != null && m.containsKey(fieldId)) {
      return m.get(fieldId);
    }
    Item item = Moaks.item(itemId);
    Field f = item == null ? null : item.field(fieldId);
    return f == null ? "" : f.normal();
  }

  public void set(String itemId, String fieldId, String value) {
    Item item = require(itemId);
    Field f = item.field(fieldId);
    if (f == null) {
      throw new IllegalArgumentException("No field " + fieldId + " on " + itemId);
    }
    if (!f.multi() && f.option(value) == null) {
      throw new IllegalArgumentException(value + " is not a valid " + f.label() + " for " + itemId);
    }
    if (f.multi()) {
      for (String code : splitFlags(value)) {
        if (f.option(code) == null) {
          throw new IllegalArgumentException(code + " is not a valid choice for " + itemId);
        }
      }
    }
    pushUndo();
    Map<String, String> m = values.computeIfAbsent(itemId, k -> new LinkedHashMap<>());
    m.put(fieldId, value);
    // A BML of size 0 has no percentage and no count.
    if (item.category() == Moaks.Category.BML && fieldId.equals("size") && value.equals("0")) {
      m.put("pct", "0");
      m.put("number", "0");
    }
    fire();
  }

  /** Turns one choice of a multi field on or off. */
  public void toggleFlag(String itemId, String fieldId, String code) {
    Set<String> flags = new LinkedHashSet<>(splitFlags(get(itemId, fieldId)));
    if (!flags.remove(code)) {
      flags.add(code);
    }
    Field f = require(itemId).field(fieldId);
    List<String> ordered = new ArrayList<>();
    for (Option o : f.options()) {
      if (flags.contains(o.code())) {
        ordered.add(o.code());
      }
    }
    set(itemId, fieldId, String.join("|", ordered));
  }

  /** Marks an item as looked at and normal, without changing anything else. */
  public void markNormal(String itemId) {
    Item item = require(itemId);
    pushUndo();
    Map<String, String> m = new LinkedHashMap<>();
    for (Field f : item.fields()) {
      m.put(f.id(), f.normal());
    }
    values.put(itemId, m);
    fire();
  }

  /** Forgets what was entered for an item: it goes back to untouched. */
  public void clear(String itemId) {
    if (values.containsKey(itemId)) {
      pushUndo();
      values.remove(itemId);
      fire();
    }
  }

  public static List<String> splitFlags(String v) {
    if (v == null || v.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String s : v.split("\\|")) {
      if (!s.isBlank()) {
        out.add(s.trim());
      }
    }
    return out;
  }

  /**
   * The colour grade of an item for the illustration: -1 untouched or nothing found, else the
   * highest grade among its fields (cartilage uses the area grade).
   */
  public int grade(String itemId) {
    if (!isEntered(itemId)) {
      return -1;
    }
    Item item = require(itemId);
    if (item.category() == Moaks.Category.BML) {
      // A BML is coloured by its size; the percentage and count describe it further.
      return Integer.parseInt(get(itemId, "size"));
    }
    int best = -1;
    for (Field f : item.fields()) {
      String v = get(itemId, f.id());
      if (f.multi()) {
        best = Math.max(best, 0);
        for (String code : splitFlags(v)) {
          Option o = f.option(code);
          if (o != null) {
            best = Math.max(best, o.grade());
          }
        }
      } else {
        Option o = f.option(v);
        if (o != null) {
          best = Math.max(best, o.grade());
        }
      }
    }
    return best;
  }

  /** Short text for a dot label or badge: "Cart 2.1 · BML 2". Empty when nothing entered. */
  public String summary(String locationKey) {
    List<String> parts = new ArrayList<>();
    for (Item item : Moaks.itemsAt(locationKey)) {
      if (!isEntered(item.id())) {
        continue;
      }
      parts.add(shortSummary(item));
    }
    return String.join(" · ", parts);
  }

  private String shortSummary(Item item) {
    String id = item.id();
    return switch (item.category()) {
      case CARTILAGE -> "Cart " + get(id, "score");
      case BML -> "BML " + get(id, "size");
      case OSTEOPHYTE -> "Ost " + get(id, "grade");
      case EXTRUSION -> "Extr " + get(id, "grade");
      case SYNOVITIS -> "Grade " + get(id, "grade");
      case MENISCUS -> {
        List<String> f = splitFlags(get(id, "morph"));
        yield f.isEmpty() ? "Normal" : labelsOf(item.field("morph"), f);
      }
      default -> {
        List<String> found = new ArrayList<>();
        for (Field f : item.fields()) {
          Option o = f.option(get(id, f.id()));
          if (o != null && o.grade() > 0) {
            boolean yes = o.code().equals("yes") || o.code().equals("present");
            found.add(yes ? f.label() : o.label());
          }
        }
        yield found.isEmpty() ? "Normal" : String.join(", ", found);
      }
    };
  }

  private static String labelsOf(Field f, List<String> codes) {
    List<String> out = new ArrayList<>();
    for (String c : codes) {
      Option o = f.option(c);
      out.add(o == null ? c : o.label());
    }
    return String.join(", ", out);
  }

  /** Worst colour grade of the items under a structure; -1 if nothing entered. */
  public int structureGrade(String structureKey) {
    Moaks.Structure st = Moaks.structure(structureKey);
    int g = -1;
    if (st != null) {
      for (String id : st.itemIds()) {
        g = Math.max(g, grade(id));
      }
    }
    return g;
  }

  /** How many items under a structure have been scored. */
  public int structureEntered(String structureKey) {
    Moaks.Structure st = Moaks.structure(structureKey);
    int n = 0;
    if (st != null) {
      for (String id : st.itemIds()) {
        if (isEntered(id)) {
          n++;
        }
      }
    }
    return n;
  }

  /** Findings above normal under a structure, e.g. "MFc cartilage 2.1, MFc BML 2". */
  public String structureSummary(String structureKey) {
    Moaks.Structure st = Moaks.structure(structureKey);
    List<String> parts = new ArrayList<>();
    if (st != null) {
      for (String id : st.itemIds()) {
        if (grade(id) > 0) {
          Item item = Moaks.item(id);
          parts.add(item.location().replace("Ost", "Ost ") + " " + shortSummary(item));
        }
      }
    }
    return String.join(", ", parts);
  }

  /** Marks every untouched item under a structure as normal. */
  public void markStructureNormal(String structureKey) {
    Moaks.Structure st = Moaks.structure(structureKey);
    if (st == null) {
      return;
    }
    pushUndo();
    for (String id : st.itemIds()) {
      if (!values.containsKey(id)) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Field f : Moaks.item(id).fields()) {
          m.put(f.id(), f.normal());
        }
        values.put(id, m);
      }
    }
    fire();
  }

  /** Number of items entered, and the total. */
  public int enteredCount() {
    return values.size();
  }

  public static int totalCount() {
    return Moaks.items().size();
  }

  public Map<String, Map<String, String>> enteredValues() {
    Map<String, Map<String, String>> copy = new LinkedHashMap<>();
    values.forEach((k, v) -> copy.put(k, Collections.unmodifiableMap(new LinkedHashMap<>(v))));
    return Collections.unmodifiableMap(copy);
  }

  /** Loads saved values without touching undo; used when reopening a read. */
  public void restore(Map<String, Map<String, String>> saved, List<Dot> savedDots) {
    values.clear();
    saved.forEach((k, v) -> {
      if (Moaks.item(k) != null) {
        values.put(k, new LinkedHashMap<>(v));
      }
    });
    dots.clear();
    dots.addAll(savedDots);
    undo.clear();
    fire();
  }

  // ---- dots ----

  public List<Dot> dots() {
    return Collections.unmodifiableList(new ArrayList<>(dots));
  }

  public Dot dot(String id) {
    for (Dot d : dots) {
      if (d.id().equals(id)) {
        return d;
      }
    }
    return null;
  }

  public List<Dot> dotsAt(String locationKey) {
    List<Dot> out = new ArrayList<>();
    for (Dot d : dots) {
      if (locationKey.equals(d.location())) {
        out.add(d);
      }
    }
    return out;
  }

  public void addDot(Dot d) {
    pushUndo();
    dots.add(d);
    fire();
  }

  public void assignDot(String dotId, String locationKey) {
    if (Moaks.structure(locationKey) == null && Moaks.location(locationKey) == null) {
      throw new IllegalArgumentException("Unknown location " + locationKey);
    }
    for (int i = 0; i < dots.size(); i++) {
      if (dots.get(i).id().equals(dotId)) {
        pushUndo();
        dots.set(i, dots.get(i).withLocation(locationKey));
        fire();
        return;
      }
    }
  }

  public void removeDot(String dotId) {
    for (int i = 0; i < dots.size(); i++) {
      if (dots.get(i).id().equals(dotId)) {
        pushUndo();
        dots.remove(i);
        fire();
        return;
      }
    }
  }

  // ---- undo and listeners ----

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public void undo() {
    Snapshot s = undo.poll();
    if (s == null) {
      return;
    }
    values.clear();
    values.putAll(s.values());
    dots.clear();
    dots.addAll(s.dots());
    fire();
  }

  private void pushUndo() {
    Map<String, Map<String, String>> copy = new LinkedHashMap<>();
    values.forEach((k, v) -> copy.put(k, new LinkedHashMap<>(v)));
    undo.push(new Snapshot(copy, new ArrayList<>(dots)));
    while (undo.size() > 200) {
      undo.removeLast();
    }
  }

  public void addListener(Runnable r) {
    listeners.add(r);
  }

  public void removeListener(Runnable r) {
    listeners.remove(r);
  }

  private void fire() {
    for (Runnable r : listeners) {
      r.run();
    }
  }

  private static Item require(String itemId) {
    Item item = Moaks.item(itemId);
    if (item == null) {
      throw new IllegalArgumentException("Unknown MOAKS item " + itemId);
    }
    return item;
  }

  private static String nz(String s) {
    return s == null ? "" : s.trim();
  }
}
