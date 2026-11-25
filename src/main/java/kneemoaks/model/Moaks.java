package kneemoaks.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MOAKS scoring sheet (Hunter et al., Osteoarthritis Cartilage 2011;19:990-1002): every item a
 * reader can score, where it sits in the knee and what each score means.
 */
public final class Moaks {

  /** Feature groups, in the order the CSV lists them. */
  public enum Category {
    CARTILAGE("Cartilage"),
    BML("Bone marrow lesion"),
    OSTEOPHYTE("Osteophyte"),
    MENISCUS("Meniscus"),
    EXTRUSION("Meniscal extrusion"),
    SYNOVITIS("Synovitis"),
    LIGAMENT("Ligaments and tendon"),
    PERIARTICULAR("Periarticular"),
    LOOSE_BODY("Loose bodies");

    public final String label;

    Category(String label) {
      this.label = label;
    }
  }

  /** Which tab of the panel shows a location. */
  public enum Tab {
    BONE("Bone & cartilage"),
    MENISCI("Menisci"),
    SOFT("Soft tissue");

    public final String label;

    Tab(String label) {
      this.label = label;
    }
  }

  /** One answer a field can take. {@code grade} drives the colour: -1 = neutral, 0..3. */
  public record Option(String code, String label, String meaning, int grade) {}

  /**
   * One thing scored on an item. A multi field holds several codes joined by "|", and an empty
   * value means none selected.
   */
  public record Field(String id, String label, List<Option> options, boolean multi, String normal) {
    public Option option(String code) {
      for (Option o : options) {
        if (o.code().equals(code)) {
          return o;
        }
      }
      return null;
    }
  }

  /** One scored item, e.g. the cartilage of MFc or the BML of MFc. */
  public record Item(String id, Category category, String location, String name, List<Field> fields) {
    public Field field(String fieldId) {
      for (Field f : fields) {
        if (f.id().equals(fieldId)) {
          return f;
        }
      }
      return null;
    }
  }

  /** A place a dot can be put: a subregion, a meniscal segment, a soft tissue structure. */
  public record Location(String key, String name, String group, Tab tab) {}

  /**
   * What the reader picks when dropping a dot: one of eight structures (patella, trochlea, femur,
   * tibia; medial or lateral), plus the whole knee for soft tissue. Each holds every MOAKS item
   * scored under it.
   */
  public record Structure(String key, String name, String part, String side, List<String> itemIds) {}

  public static final String WHOLE_KNEE = "WHOLE";

  public static final List<String> CARTILAGE_CODES =
      List.of("0.0", "1.0", "1.1", "2.0", "2.1", "2.2", "3.0", "3.1", "3.2", "3.3");

  private static final String[] AREA = {
    "none", "<10% of the subregion", "10-75% of the subregion", ">75% of the subregion"
  };
  private static final String[] FT = {
    "no full-thickness loss",
    "full-thickness <10%",
    "full-thickness 10-75%",
    "full-thickness >75%"
  };

  private static final Map<String, Location> LOCATIONS = new LinkedHashMap<>();
  private static final Map<String, Item> ITEMS = new LinkedHashMap<>();
  private static final Map<String, Structure> STRUCTURES = new LinkedHashMap<>();

  static {
    // Cartilage and BML subregions
    String[][] sub = {
      {"PM", "Patella, medial", "PATELLA"},
      {"PL", "Patella, lateral", "PATELLA"},
      {"MFa", "Medial femur, trochlea", "FEMUR"},
      {"MFc", "Medial femur, central", "FEMUR"},
      {"MFp", "Medial femur, posterior", "FEMUR"},
      {"LFa", "Lateral femur, trochlea", "FEMUR"},
      {"LFc", "Lateral femur, central", "FEMUR"},
      {"LFp", "Lateral femur, posterior", "FEMUR"},
      {"MTa", "Medial tibia, anterior", "TIBIA"},
      {"MTc", "Medial tibia, central", "TIBIA"},
      {"MTp", "Medial tibia, posterior", "TIBIA"},
      {"LTa", "Lateral tibia, anterior", "TIBIA"},
      {"LTc", "Lateral tibia, central", "TIBIA"},
      {"LTp", "Lateral tibia, posterior", "TIBIA"},
      {"TSS", "Tibia, subspinous", "TIBIA"},
    };
    for (String[] s : sub) {
      loc(s[0], s[1], s[2], Tab.BONE);
    }
    for (String[] s : sub) {
      if (!s[0].equals("TSS")) {
        item("cart." + s[0], Category.CARTILAGE, s[0], "Cartilage " + s[0], cartilageField());
      }
    }
    for (String[] s : sub) {
      item(
          "bml." + s[0],
          Category.BML,
          s[0],
          "BML " + s[0],
          grade("size", "Size", "none", "<33% of the subregion", "33-66%", ">66%"),
          grade("pct", "% of it that is BML", "none", "<33%", "33-66%", ">66%"),
          grade("number", "Number", "none", "1", "2", "3 or more"));
    }

    // Osteophytes
    String[][] ost = {
      {"OstTrM", "Osteophyte, trochlea medial", "FEMUR"},
      {"OstTrL", "Osteophyte, trochlea lateral", "FEMUR"},
      {"OstFCM", "Osteophyte, central femur medial", "FEMUR"},
      {"OstFCL", "Osteophyte, central femur lateral", "FEMUR"},
      {"OstFPM", "Osteophyte, posterior femur medial", "FEMUR"},
      {"OstFPL", "Osteophyte, posterior femur lateral", "FEMUR"},
      {"OstPS", "Osteophyte, patella superior", "PATELLA"},
      {"OstPI", "Osteophyte, patella inferior", "PATELLA"},
      {"OstPM", "Osteophyte, patella medial", "PATELLA"},
      {"OstPL", "Osteophyte, patella lateral", "PATELLA"},
      {"OstTM", "Osteophyte, tibia medial", "TIBIA"},
      {"OstTL", "Osteophyte, tibia lateral", "TIBIA"},
    };
    for (String[] o : ost) {
      loc(o[0], o[1], "OSTEOPHYTE", Tab.BONE);
      item(
          "ost." + o[0],
          Category.OSTEOPHYTE,
          o[0],
          o[1],
          grade("grade", "Size", "none", "small", "medium", "large"));
    }

    // Menisci
    String[][] men = {
      {"MM-AH", "Medial meniscus, anterior horn"},
      {"MM-B", "Medial meniscus, body"},
      {"MM-PH", "Medial meniscus, posterior horn"},
      {"LM-AH", "Lateral meniscus, anterior horn"},
      {"LM-B", "Lateral meniscus, body"},
      {"LM-PH", "Lateral meniscus, posterior horn"},
    };
    for (String[] m : men) {
      loc(m[0], m[1], "MENISCUS", Tab.MENISCI);
      boolean root = !m[0].endsWith("-B");
      List<Option> flags = new ArrayList<>();
      flags.add(new Option("signal", "Signal", "intrasubstance signal", 1));
      flags.add(flag("radial", "Radial", "radial tear"));
      flags.add(flag("horizontal", "Horizontal", "horizontal tear"));
      flags.add(flag("vertical", "Vertical", "vertical tear"));
      flags.add(flag("complex", "Complex", "complex tear"));
      if (root) {
        flags.add(flag("root", "Root tear", "root tear"));
      }
      flags.add(flag("partial_mac", "Partial maceration", "partial maceration"));
      flags.add(new Option("progressive_mac", "Progressive maceration", "progressive partial maceration", 3));
      flags.add(new Option("complete_mac", "Complete maceration", "complete maceration", 3));
      flags.add(flag("cyst", "Cyst", "meniscal cyst"));
      flags.add(flag("hypertrophy", "Hypertrophy", "hypertrophy"));
      item(
          "men." + m[0],
          Category.MENISCUS,
          m[0],
          m[1],
          new Field("morph", "Morphology", List.copyOf(flags), true, ""));
    }
    String[][] ext = {
      {"ext.MM-med", "MM-B", "Medial meniscus, medial extrusion"},
      {"ext.MM-ant", "MM-AH", "Medial meniscus, anterior extrusion"},
      {"ext.LM-lat", "LM-B", "Lateral meniscus, lateral extrusion"},
      {"ext.LM-ant", "LM-AH", "Lateral meniscus, anterior extrusion"},
    };
    for (String[] e : ext) {
      item(
          e[0],
          Category.EXTRUSION,
          e[1],
          e[2],
          grade("grade", "Extrusion", "<2 mm", "2-2.9 mm", "3-4.9 mm", "5 mm or more"));
    }

    // Soft tissue
    loc("EFF", "Effusion-synovitis", "SOFT TISSUE", Tab.SOFT);
    item(
        "syn.effusion",
        Category.SYNOVITIS,
        "EFF",
        "Effusion-synovitis",
        grade("grade", "Grade", "normal", "small", "medium", "large"));
    loc("HOF", "Hoffa-synovitis", "SOFT TISSUE", Tab.SOFT);
    item(
        "syn.hoffa",
        Category.SYNOVITIS,
        "HOF",
        "Hoffa-synovitis",
        grade("grade", "Grade", "normal", "mild", "moderate", "severe"));
    loc("ACL", "Anterior cruciate ligament", "SOFT TISSUE", Tab.SOFT);
    item(
        "lig.ACL",
        Category.LIGAMENT,
        "ACL",
        "ACL",
        choice("tear", "Tear", "0", opt("0", "Normal", "normal", 0), opt("1", "Complete tear", "complete tear", 3)),
        yesNo("assoc", "Associated BML or cyst"),
        yesNo("repair", "Reconstructed"));
    loc("PCL", "Posterior cruciate ligament", "SOFT TISSUE", Tab.SOFT);
    item(
        "lig.PCL",
        Category.LIGAMENT,
        "PCL",
        "PCL",
        choice("tear", "Tear", "0", opt("0", "Normal", "normal", 0), opt("1", "Complete tear", "complete tear", 3)),
        yesNo("assoc", "Associated BML"));
    loc("PT", "Patellar tendon", "SOFT TISSUE", Tab.SOFT);
    item("lig.PT", Category.LIGAMENT, "PT", "Patellar tendon signal", presence("signal", "Signal"));
    String[][] peri = {
      {"PES", "Pes anserine bursitis"},
      {"ITB", "Iliotibial band signal"},
      {"POP", "Popliteal cyst"},
      {"IPB", "Infrapatellar bursa"},
      {"PPB", "Prepatellar bursa"},
      {"GTF", "Ganglion cyst, tibiofibular"},
      {"GCR", "Ganglion cyst, cruciate"},
      {"GOT", "Ganglion cyst, other"},
    };
    for (String[] p : peri) {
      loc(p[0], p[1], "SOFT TISSUE", Tab.SOFT);
      item("peri." + p[0], Category.PERIARTICULAR, p[0], p[1], presence("present", "Present"));
    }
    loc("LB", "Loose bodies", "SOFT TISSUE", Tab.SOFT);
    item("lb", Category.LOOSE_BODY, "LB", "Loose bodies", presence("present", "Present"));

    // The eight structures of the dot card, and the whole knee
    structure("PAT-M", "Medial patella", "Patella", "Medial",
        "cart.PM", "bml.PM", "ost.OstPM", "ost.OstPS", "ost.OstPI");
    structure("PAT-L", "Lateral patella", "Patella", "Lateral",
        "cart.PL", "bml.PL", "ost.OstPL", "ost.OstPS", "ost.OstPI");
    structure("TRO-M", "Medial trochlea", "Trochlea", "Medial", "cart.MFa", "bml.MFa", "ost.OstTrM");
    structure("TRO-L", "Lateral trochlea", "Trochlea", "Lateral", "cart.LFa", "bml.LFa", "ost.OstTrL");
    structure("FEM-M", "Medial femur", "Femur", "Medial",
        "cart.MFc", "cart.MFp", "bml.MFc", "bml.MFp", "ost.OstFCM", "ost.OstFPM");
    structure("FEM-L", "Lateral femur", "Femur", "Lateral",
        "cart.LFc", "cart.LFp", "bml.LFc", "bml.LFp", "ost.OstFCL", "ost.OstFPL");
    structure("TIB-M", "Medial tibia", "Tibia", "Medial",
        "cart.MTa", "cart.MTc", "cart.MTp", "bml.MTa", "bml.MTc", "bml.MTp", "bml.TSS", "ost.OstTM",
        "men.MM-AH", "men.MM-B", "men.MM-PH", "ext.MM-med", "ext.MM-ant");
    structure("TIB-L", "Lateral tibia", "Tibia", "Lateral",
        "cart.LTa", "cart.LTc", "cart.LTp", "bml.LTa", "bml.LTc", "bml.LTp", "bml.TSS", "ost.OstTL",
        "men.LM-AH", "men.LM-B", "men.LM-PH", "ext.LM-lat", "ext.LM-ant");
    List<String> whole = new ArrayList<>();
    for (Item i : ITEMS.values()) {
      if (LOCATIONS.get(i.location()).tab() == Tab.SOFT) {
        whole.add(i.id());
      }
    }
    STRUCTURES.put(WHOLE_KNEE, new Structure(WHOLE_KNEE, "Whole knee", "Whole knee", "",
        List.copyOf(whole)));
  }

  private static void structure(String key, String name, String part, String side, String... ids) {
    for (String id : ids) {
      if (!ITEMS.containsKey(id)) {
        throw new IllegalStateException("Unknown item " + id + " in " + key);
      }
    }
    STRUCTURES.put(key, new Structure(key, name, part, side, List.of(ids)));
  }

  /** The eight structures offered when a dot is dropped, then the whole knee. */
  public static List<Structure> structures() {
    return List.copyOf(STRUCTURES.values());
  }

  public static Structure structure(String key) {
    return key == null ? null : STRUCTURES.get(key);
  }

  /** The structure a subregion belongs to (MFc -> FEM-M), for dots saved by older versions. */
  public static String structureOfLocation(String locationKey) {
    if (STRUCTURES.containsKey(locationKey)) {
      return locationKey;
    }
    for (Structure s : STRUCTURES.values()) {
      for (String id : s.itemIds()) {
        if (ITEMS.get(id).location().equals(locationKey)) {
          return s.key();
        }
      }
    }
    return null;
  }

  private Moaks() {}

  public static List<Item> items() {
    return List.copyOf(ITEMS.values());
  }

  public static Item item(String id) {
    return ITEMS.get(id);
  }

  public static List<Location> locations() {
    return List.copyOf(LOCATIONS.values());
  }

  public static Location location(String key) {
    return LOCATIONS.get(key);
  }

  /** Items scored at a location, cartilage before BML. */
  public static List<Item> itemsAt(String locationKey) {
    List<Item> list = new ArrayList<>();
    for (Item i : ITEMS.values()) {
      if (i.location().equals(locationKey)) {
        list.add(i);
      }
    }
    return Collections.unmodifiableList(list);
  }

  /** Finds a location from what a reader types: its key or words of its name, any case. */
  public static List<Location> search(String text) {
    String q = text == null ? "" : text.trim().toLowerCase().replace('-', ' ');
    List<Location> exact = new ArrayList<>();
    List<Location> rest = new ArrayList<>();
    for (Location l : LOCATIONS.values()) {
      String key = l.key().toLowerCase().replace('-', ' ');
      String hay = key + " " + l.name().toLowerCase().replace(',', ' ') + " " + l.group().toLowerCase();
      if (q.isEmpty()) {
        rest.add(l);
      } else if (key.equals(q) || key.replace(" ", "").equals(q.replace(" ", ""))) {
        exact.add(l);
      } else if (containsAllWords(hay, q) || key.startsWith(q)) {
        rest.add(l);
      }
    }
    exact.addAll(rest);
    return exact;
  }

  private static boolean containsAllWords(String hay, String q) {
    for (String w : q.split("\\s+")) {
      if (!w.isEmpty() && !hay.contains(w)) {
        return false;
      }
    }
    return true;
  }

  /** Splits a cartilage code "2.1" into area grade 2 and full-thickness grade 1. */
  public static int[] splitCartilage(String code) {
    if (code == null || code.length() != 3 || code.charAt(1) != '.') {
      return null;
    }
    return new int[] {code.charAt(0) - '0', code.charAt(2) - '0'};
  }

  /** Turns two typed digits ("21") or a code ("2.1") into a valid cartilage code, else null. */
  public static String parseCartilage(String typed) {
    if (typed == null) {
      return null;
    }
    String t = typed.trim().replace(',', '.');
    if (t.length() == 2 && Character.isDigit(t.charAt(0)) && Character.isDigit(t.charAt(1))) {
      t = t.charAt(0) + "." + t.charAt(1);
    } else if (t.length() == 1 && Character.isDigit(t.charAt(0))) {
      t = t + ".0";
    }
    return CARTILAGE_CODES.contains(t) ? t : null;
  }

  // ---- builders ----

  private static void loc(String key, String name, String group, Tab tab) {
    LOCATIONS.put(key, new Location(key, name, group, tab));
  }

  private static void item(String id, Category c, String location, String name, Field... fields) {
    ITEMS.put(id, new Item(id, c, location, name, List.of(fields)));
  }

  private static Field cartilageField() {
    List<Option> opts = new ArrayList<>();
    for (String code : CARTILAGE_CODES) {
      int[] g = splitCartilage(code);
      String meaning = "area " + AREA[g[0]] + ", " + FT[g[1]];
      opts.add(new Option(code, code, meaning, g[0]));
    }
    return new Field("score", "Area.full-thickness", List.copyOf(opts), false, "0.0");
  }

  private static Field grade(String id, String label, String g0, String g1, String g2, String g3) {
    return new Field(
        id,
        label,
        List.of(opt("0", "0", g0, 0), opt("1", "1", g1, 1), opt("2", "2", g2, 2), opt("3", "3", g3, 3)),
        false,
        "0");
  }

  private static Field choice(String id, String label, String normal, Option... options) {
    return new Field(id, label, List.of(options), false, normal);
  }

  private static Field yesNo(String id, String label) {
    return new Field(
        id, label, List.of(opt("no", "No", "no", -1), opt("yes", "Yes", "yes", 2)), false, "no");
  }

  private static Field presence(String id, String label) {
    return new Field(
        id,
        label,
        List.of(opt("absent", "Absent", "absent", -1), opt("present", "Present", "present", 2)),
        false,
        "absent");
  }

  private static Option opt(String code, String label, String meaning, int grade) {
    return new Option(code, label, meaning, grade);
  }

  private static Option flag(String code, String label, String meaning) {
    return new Option(code, label, meaning, 2);
  }
}
