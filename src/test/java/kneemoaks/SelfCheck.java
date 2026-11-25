package kneemoaks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kneemoaks.model.CsvStore;
import kneemoaks.model.Dot;
import kneemoaks.model.Moaks;
import kneemoaks.model.ScoreSheet;
import kneemoaks.model.StudyInfo;

/** Plain-Java checks of the scoring model and CSV files; run with {@code java kneemoaks.SelfCheck}. */
public final class SelfCheck {

  private static int passed;

  public static void main(String[] args) throws Exception {
    counts();
    cartilageCodes();
    sheetRules();
    search();
    kneeSide();
    csvRoundTrip();
    structures();
    System.out.println("All " + passed + " checks passed");
  }

  static void counts() {
    long cart = Moaks.items().stream().filter(i -> i.category() == Moaks.Category.CARTILAGE).count();
    long bml = Moaks.items().stream().filter(i -> i.category() == Moaks.Category.BML).count();
    long ost = Moaks.items().stream().filter(i -> i.category() == Moaks.Category.OSTEOPHYTE).count();
    long men = Moaks.items().stream().filter(i -> i.category() == Moaks.Category.MENISCUS).count();
    long ext = Moaks.items().stream().filter(i -> i.category() == Moaks.Category.EXTRUSION).count();
    check(cart == 14, "14 cartilage subregions, got " + cart);
    check(bml == 15, "15 BML subregions, got " + bml);
    check(ost == 12, "12 osteophyte sites, got " + ost);
    check(men == 6, "6 meniscal segments, got " + men);
    check(ext == 4, "4 extrusion items, got " + ext);
    check(Moaks.items().size() == 65, "65 items in all, got " + Moaks.items().size());
    check(Moaks.item("men.MM-B").field("morph").option("root") == null, "no root tear in the body");
    check(Moaks.item("men.MM-PH").field("morph").option("root") != null, "root tear in the PH");
    for (Moaks.Item i : Moaks.items()) {
      check(Moaks.location(i.location()) != null, "item " + i.id() + " has a known location");
    }
  }

  static void cartilageCodes() {
    check("2.1".equals(Moaks.parseCartilage("21")), "21 -> 2.1");
    check("3.3".equals(Moaks.parseCartilage("3.3")), "3.3 stays");
    check("1.0".equals(Moaks.parseCartilage("1")), "1 -> 1.0");
    check(Moaks.parseCartilage("23") == null, "2.3 is not valid (full thickness > area)");
    check(Moaks.parseCartilage("4") == null, "4 is not valid");
    check(Moaks.CARTILAGE_CODES.size() == 10, "10 cartilage codes");
  }

  static void sheetRules() {
    ScoreSheet s = new ScoreSheet();
    s.reset(StudyInfo.NONE, "Right", "Baseline", "AB");
    check(s.get("cart.MFc", "score").equals("0.0"), "untouched cartilage reads 0.0");
    check(!s.isEntered("cart.MFc"), "untouched is not entered");
    s.set("cart.MFc", "score", "2.1");
    s.set("bml.MFc", "size", "2");
    s.set("bml.MFc", "pct", "3");
    check(s.summary("MFc").equals("Cart 2.1 · BML 2"), "summary: " + s.summary("MFc"));
    check(s.grade("cart.MFc") == 2, "cartilage colour grade is the area grade");
    s.set("bml.MFc", "size", "0");
    check(s.get("bml.MFc", "pct").equals("0"), "BML size 0 clears the percentage");
    s.undo();
    check(s.get("bml.MFc", "size").equals("2") && s.get("bml.MFc", "pct").equals("3"), "undo");
    boolean threw = false;
    try {
      s.set("cart.MFc", "score", "2.3");
    } catch (IllegalArgumentException e) {
      threw = true;
    }
    check(threw, "invalid cartilage code refused");
    s.toggleFlag("men.MM-PH", "morph", "partial_mac");
    s.toggleFlag("men.MM-PH", "morph", "horizontal");
    check(s.get("men.MM-PH", "morph").equals("horizontal|partial_mac"), "flags keep sheet order");
    s.toggleFlag("men.MM-PH", "morph", "horizontal");
    check(s.get("men.MM-PH", "morph").equals("partial_mac"), "flag toggles off");
    s.markNormal("syn.effusion");
    check(s.isEntered("syn.effusion") && s.grade("syn.effusion") == 0, "mark normal");
    s.set("lig.ACL", "repair", "yes");
    check(s.summary("ACL").equals("Reconstructed"), "ACL summary: " + s.summary("ACL"));
    Dot d = Dot.create(1, 2, 3, "1.2.3", "4.5", "6.7", 10, 20);
    s.addDot(d);
    check(s.dot(d.id()).location() == null, "new dot has no location");
    s.assignDot(d.id(), "MFc");
    check(s.dotsAt("MFc").size() == 1, "dot assigned to MFc");
  }

  static void structures() {
    check(Moaks.structures().size() == 9, "8 structures plus the whole knee");
    java.util.Set<String> covered = new java.util.HashSet<>();
    for (Moaks.Structure st : Moaks.structures()) {
      covered.addAll(st.itemIds());
    }
    check(covered.size() == Moaks.items().size(), "every item sits under a structure: " + covered.size());
    check(Moaks.structureOfLocation("MFc").equals("FEM-M"), "MFc is in the medial femur");
    check(Moaks.structureOfLocation("MFa").equals("TRO-M"), "MFa is in the medial trochlea");
    check(Moaks.structure("TIB-M").itemIds().contains("men.MM-PH"), "medial meniscus under medial tibia");
    ScoreSheet s = new ScoreSheet();
    s.reset(StudyInfo.NONE, "Right", "Baseline", "AB");
    s.set("cart.MFc", "score", "2.1");
    s.set("bml.MFp", "size", "1");
    check(s.structureGrade("FEM-M") == 2, "structure grade is the worst item");
    check(s.structureEntered("FEM-M") == 2, "two items entered under FEM-M");
    check(s.structureSummary("FEM-M").equals("MFc Cart 2.1, MFp BML 1"),
        "structure summary: " + s.structureSummary("FEM-M"));
    s.markStructureNormal("FEM-M");
    check(s.structureEntered("FEM-M") == 6 && s.get("cart.MFc", "score").equals("2.1"),
        "mark the rest normal keeps entered scores");
    Dot d = Dot.create(0, 0, 0, "", "", "", 0, 0);
    s.addDot(d);
    s.assignDot(d.id(), "TIB-L");
    check(s.dotsAt("TIB-L").size() == 1, "dot assigned to a structure");
  }

  static void search() {
    check(Moaks.search("mfc").get(0).key().equals("MFc"), "search mfc");
    check(Moaks.search("MM PH").get(0).key().equals("MM-PH"), "search MM PH");
    check(Moaks.search("mmph").get(0).key().equals("MM-PH"), "search mmph");
    check(Moaks.search("medial meniscus posterior").get(0).key().equals("MM-PH"),
        "search by words");
    check(Moaks.search("effusion").get(0).key().equals("EFF"), "search effusion");
    check(Moaks.search("").size() == Moaks.locations().size(), "empty search lists all");
  }

  static void kneeSide() {
    check(new StudyInfo("", "", "", "", "", "SAG PD FS RT KNEE", "").guessKneeSide().equals("Right"),
        "RT in description");
    check(new StudyInfo("", "", "", "", "", "", "L").guessKneeSide().equals("Left"), "laterality L");
    check(new StudyInfo("", "", "", "", "", "SAG PD FS", "").guessKneeSide().isEmpty(),
        "no side in PD FS");
  }

  static void csvRoundTrip() throws Exception {
    Path dir = Files.createTempDirectory("kneemoaks");
    CsvStore store = new CsvStore(dir);
    StudyInfo st = new StudyInfo("P1", "A1", "20261120", "1.2.3", "1.2.3.4", "SAG PD, FS \"fat sat\"", "R");
    ScoreSheet s = new ScoreSheet();
    s.reset(st, "Right", "Baseline", "AB");
    s.set("cart.MFc", "score", "2.1");
    s.toggleFlag("men.MM-PH", "morph", "horizontal");
    Dot d = Dot.create(-12.5, 40.25, 3, "9.9", "1.2.3.4", "1.2.3.4.5", 100.5, 200.25);
    s.addDot(d);
    s.assignDot(d.id(), "MFc");
    store.save(s, false);

    List<String> longLines = Files.readAllLines(dir.resolve(CsvStore.LONG_FILE));
    check(longLines.size() == 3, "draft writes only entered fields: " + longLines.size());

    // Another reader's read of the same study must survive a save.
    ScoreSheet other = new ScoreSheet();
    other.reset(st, "Right", "Baseline", "CD");
    other.set("syn.effusion", "grade", "2");
    store.save(other, false);

    s.set("cart.MFc", "score", "3.2");
    store.save(s, true);
    int fields = Moaks.items().stream().mapToInt(i -> i.fields().size()).sum();
    longLines = Files.readAllLines(dir.resolve(CsvStore.LONG_FILE));
    check(longLines.size() == 1 + fields + 1, "final writes every field once plus reader CD's row: "
        + longLines.size());

    ScoreSheet back = new ScoreSheet();
    back.reset(st, "Right", "Baseline", "ab");
    check(store.load(back), "load finds the read (reader case ignored)");
    check(back.get("cart.MFc", "score").equals("3.2"), "loaded cartilage");
    check(back.get("men.MM-PH", "morph").equals("horizontal"), "loaded flags");
    check(!back.isEntered("cart.MFa"), "normals filled at finish are not marked entered");
    check(back.dots().size() == 1 && back.dots().get(0).location().equals("FEM-M"), "loaded dot (old subregion dots map to their structure)");
    check(Math.abs(back.dots().get(0).x() + 12.5) < 1e-9, "dot x kept");

    ScoreSheet cd = new ScoreSheet();
    cd.reset(st, "Right", "Baseline", "CD");
    store.load(cd);
    check(cd.get("syn.effusion", "grade").equals("2") && !cd.isEntered("cart.MFc"),
        "reader CD kept separately");

    List<String> wide = Files.readAllLines(dir.resolve(CsvStore.WIDE_FILE));
    check(wide.size() == 3, "wide file: header + 2 reads, got " + wide.size());
    check(wide.get(0).contains("cart.MFc.score"), "wide header has item columns");
  }

  private static void check(boolean ok, String what) {
    if (!ok) {
      throw new AssertionError("FAILED: " + what);
    }
    passed++;
  }
}
