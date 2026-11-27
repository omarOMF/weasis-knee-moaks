package kneemoaks.model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import kneemoaks.model.Moaks.Field;
import kneemoaks.model.Moaks.Item;
import kneemoaks.model.Moaks.Option;

/**
 * Saves reads to three CSV files in one folder, replacing the earlier rows of the same read (same
 * study, knee, visit and reader):
 *
 * <ul>
 *   <li>{@code knee_moaks_long.csv}: one row per scored field
 *   <li>{@code knee_moaks_wide.csv}: one row per read, one column per field
 *   <li>{@code knee_moaks_markers.csv}: one row per marker, with its position in patient mm
 * </ul>
 */
public final class CsvStore {

  public static final String LONG_FILE = "knee_moaks_long.csv";
  public static final String WIDE_FILE = "knee_moaks_wide.csv";
  public static final String DOTS_FILE = "knee_moaks_markers.csv";
  /** Name used by builds before markers were called markers; still read. */
  static final String OLD_DOTS_FILE = "knee_moaks_dots.csv";

  public static final String STATUS_DRAFT = "in progress";
  public static final String STATUS_FINAL = "final";

  static final List<String> HEADER = List.of(
      "saved_at", "status", "reader", "patient_id", "accession_number", "study_date",
      "study_instance_uid", "series_instance_uid", "series_description", "knee_side", "visit");

  static final List<String> LONG_COLUMNS = concat(HEADER, List.of(
      "category", "item_id", "location", "item_name", "field", "value", "meaning", "entered"));

  static final List<String> DOT_COLUMNS = concat(HEADER, List.of(
      "marker_id", "location", "location_name", "scores", "x_mm", "y_mm", "z_mm",
      "frame_of_reference_uid", "marker_series_instance_uid", "sop_instance_uid", "image_x", "image_y"));

  private final Path folder;

  public CsvStore(Path folder) {
    this.folder = folder;
  }

  public Path folder() {
    return folder;
  }

  public static List<String> wideColumns() {
    List<String> cols = new ArrayList<>(HEADER);
    for (Item item : Moaks.items()) {
      for (Field f : item.fields()) {
        cols.add(item.id() + "." + f.id());
      }
    }
    cols.add("n_entered");
    cols.add("n_markers");
    return cols;
  }

  /**
   * Writes the read. A draft keeps only what was entered; a final read writes every item, with
   * untouched ones as normal (entered = no).
   */
  public void save(ScoreSheet sheet, boolean finished) throws IOException {
    Files.createDirectories(folder);
    String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substring(0, 19);
    String status = finished ? STATUS_FINAL : STATUS_DRAFT;
    Map<String, String> head = header(sheet, now, status);
    String key = sheet.readKey();

    List<Map<String, String>> longRows = new ArrayList<>();
    Map<String, String> wide = new LinkedHashMap<>(head);
    for (Item item : Moaks.items()) {
      boolean entered = sheet.isEntered(item.id());
      for (Field f : item.fields()) {
        String v = sheet.get(item.id(), f.id());
        if (entered || finished) {
          Map<String, String> row = new LinkedHashMap<>(head);
          row.put("category", item.category().label);
          row.put("item_id", item.id());
          row.put("location", item.location());
          row.put("item_name", item.name());
          row.put("field", f.id());
          row.put("value", v);
          row.put("meaning", meaning(f, v));
          row.put("entered", entered ? "yes" : "no");
          longRows.add(row);
          wide.put(item.id() + "." + f.id(), v);
        }
      }
    }
    wide.put("n_entered", String.valueOf(sheet.enteredCount()));
    wide.put("n_markers", String.valueOf(sheet.dots().size()));

    List<Map<String, String>> dotRows = new ArrayList<>();
    for (Dot d : sheet.dots()) {
      Map<String, String> row = new LinkedHashMap<>(head);
      Moaks.Structure st = Moaks.structure(d.location());
      Moaks.Location loc = d.location() == null ? null : Moaks.location(d.location());
      row.put("marker_id", d.id());
      row.put("location", d.location() == null ? "" : d.location());
      row.put("location_name", st != null ? st.name() : loc == null ? "" : loc.name());
      row.put("scores", d.location() == null ? ""
          : st != null ? sheet.structureSummary(st.key()) : sheet.summary(d.location()));
      row.put("x_mm", fmt(d.x()));
      row.put("y_mm", fmt(d.y()));
      row.put("z_mm", fmt(d.z()));
      row.put("frame_of_reference_uid", nz(d.frameOfReferenceUid()));
      row.put("marker_series_instance_uid", nz(d.seriesUid()));
      row.put("sop_instance_uid", nz(d.sopUid()));
      row.put("image_x", fmt(d.imageX()));
      row.put("image_y", fmt(d.imageY()));
      dotRows.add(row);
    }

    replace(folder.resolve(LONG_FILE), LONG_COLUMNS, key, longRows);
    replace(folder.resolve(WIDE_FILE), wideColumns(), key, List.of(wide));
    replace(folder.resolve(DOTS_FILE), DOT_COLUMNS, key, dotRows);
  }

  /** Reloads what was entered for this read, if anything was saved. Returns false if none. */
  public boolean load(ScoreSheet sheet) throws IOException {
    String key = sheet.readKey();
    Map<String, Map<String, String>> values = new LinkedHashMap<>();
    boolean found = false;
    for (Map<String, String> row : read(folder.resolve(LONG_FILE))) {
      if (!key.equals(rowKey(row))) {
        continue;
      }
      found = true;
      if ("yes".equals(row.get("entered")) && Moaks.item(row.get("item_id")) != null) {
        values.computeIfAbsent(row.get("item_id"), k -> new LinkedHashMap<>())
            .put(row.get("field"), nz(row.get("value")));
      }
    }
    List<Dot> dots = new ArrayList<>();
    Path markersFile = folder.resolve(DOTS_FILE);
    if (!Files.exists(markersFile)) {
      markersFile = folder.resolve(OLD_DOTS_FILE);
    }
    for (Map<String, String> row : read(markersFile)) {
      if (!key.equals(rowKey(row))) {
        continue;
      }
      found = true;
      try {
        String loc = Moaks.structureOfLocation(nz(row.get("location")));
        dots.add(new Dot(
            first(row, "marker_id", "dot_id"),
            loc,
            Double.parseDouble(row.get("x_mm")),
            Double.parseDouble(row.get("y_mm")),
            Double.parseDouble(row.get("z_mm")),
            nz(row.get("frame_of_reference_uid")),
            nz(first(row, "marker_series_instance_uid", "dot_series_instance_uid")),
            nz(row.get("sop_instance_uid")),
            parse(row.get("image_x")),
            parse(row.get("image_y"))));
      } catch (RuntimeException e) {
        // A hand-edited row that no longer parses is skipped, not fatal.
      }
    }
    if (found) {
      sheet.restore(values, dots);
    }
    return found;
  }

  // ---- helpers ----

  private static Map<String, String> header(ScoreSheet s, String now, String status) {
    StudyInfo st = s.study();
    Map<String, String> m = new LinkedHashMap<>();
    m.put("saved_at", now);
    m.put("status", status);
    m.put("reader", s.reader());
    m.put("patient_id", st.patientId());
    m.put("accession_number", st.accessionNumber());
    m.put("study_date", st.studyDate());
    m.put("study_instance_uid", st.studyInstanceUid());
    m.put("series_instance_uid", st.seriesInstanceUid());
    m.put("series_description", st.seriesDescription());
    m.put("knee_side", s.kneeSide());
    m.put("visit", s.visit());
    return m;
  }

  static String rowKey(Map<String, String> row) {
    StudyInfo st = new StudyInfo(
        row.get("patient_id"), row.get("accession_number"), row.get("study_date"),
        row.get("study_instance_uid"), row.get("series_instance_uid"),
        row.get("series_description"), "");
    return String.join("|", st.studyKey(),
        nz(row.get("knee_side")).toLowerCase(Locale.ROOT),
        nz(row.get("visit")).toLowerCase(Locale.ROOT),
        nz(row.get("reader")).toLowerCase(Locale.ROOT));
  }

  private static String meaning(Field f, String v) {
    if (f.multi()) {
      List<String> codes = ScoreSheet.splitFlags(v);
      if (codes.isEmpty()) {
        return "none";
      }
      List<String> out = new ArrayList<>();
      for (String c : codes) {
        Option o = f.option(c);
        out.add(o == null ? c : o.meaning());
      }
      return String.join("; ", out);
    }
    Option o = f.option(v);
    return o == null ? "" : o.meaning();
  }

  private void replace(Path file, List<String> columns, String key, List<Map<String, String>> rows)
      throws IOException {
    List<Map<String, String>> kept = new ArrayList<>();
    List<String> cols = new ArrayList<>(columns);
    if (Files.exists(file)) {
      List<String> oldCols = readHeader(file);
      for (String c : oldCols) {
        if (!cols.contains(c)) {
          cols.add(c); // keep columns a newer or hand-edited file may have
        }
      }
      for (Map<String, String> row : read(file)) {
        if (!key.equals(rowKey(row))) {
          kept.add(row);
        }
      }
    }
    kept.addAll(rows);
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    try (BufferedWriter w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
      w.write('﻿'); // lets Excel open UTF-8 correctly
      writeLine(w, cols);
      for (Map<String, String> row : kept) {
        List<String> line = new ArrayList<>(cols.size());
        for (String c : cols) {
          line.add(nz(row.get(c)));
        }
        writeLine(w, line);
      }
    }
    try {
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static List<String> readHeader(Path file) throws IOException {
    try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      List<List<String>> recs = parse(r, 1);
      return recs.isEmpty() ? List.of() : recs.get(0);
    }
  }

  static List<Map<String, String>> read(Path file) throws IOException {
    List<Map<String, String>> out = new ArrayList<>();
    if (!Files.exists(file)) {
      return out;
    }
    try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      List<List<String>> recs = parse(r, Integer.MAX_VALUE);
      if (recs.isEmpty()) {
        return out;
      }
      List<String> cols = recs.get(0);
      for (int i = 1; i < recs.size(); i++) {
        List<String> rec = recs.get(i);
        if (rec.size() == 1 && rec.get(0).isEmpty()) {
          continue;
        }
        Map<String, String> row = new LinkedHashMap<>();
        for (int c = 0; c < cols.size(); c++) {
          row.put(cols.get(c), c < rec.size() ? rec.get(c) : "");
        }
        out.add(row);
      }
    }
    return out;
  }

  /** RFC 4180 parser: quoted fields, doubled quotes, line breaks inside quotes. */
  static List<List<String>> parse(BufferedReader r, int maxRecords) throws IOException {
    List<List<String>> recs = new ArrayList<>();
    List<String> rec = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean quoted = false;
    boolean first = true;
    int ch;
    while ((ch = r.read()) != -1) {
      if (first) {
        first = false;
        if (ch == '﻿') {
          continue;
        }
      }
      char c = (char) ch;
      if (quoted) {
        if (c == '"') {
          r.mark(1);
          int next = r.read();
          if (next == '"') {
            cur.append('"');
          } else {
            quoted = false;
            if (next != -1) {
              r.reset();
            }
          }
        } else {
          cur.append(c);
        }
      } else if (c == '"') {
        quoted = true;
      } else if (c == ',') {
        rec.add(cur.toString());
        cur.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r') {
          r.mark(1);
          if (r.read() != '\n') {
            r.reset();
          }
        }
        rec.add(cur.toString());
        cur.setLength(0);
        recs.add(rec);
        rec = new ArrayList<>();
        if (recs.size() >= maxRecords) {
          return recs;
        }
      } else {
        cur.append(c);
      }
    }
    if (cur.length() > 0 || !rec.isEmpty()) {
      rec.add(cur.toString());
      recs.add(rec);
    }
    return recs;
  }

  private static void writeLine(BufferedWriter w, List<String> fields) throws IOException {
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        w.write(',');
      }
      String f = fields.get(i);
      if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0
          || f.indexOf('\r') >= 0) {
        w.write('"');
        w.write(f.replace("\"", "\"\""));
        w.write('"');
      } else {
        w.write(f);
      }
    }
    w.write("\r\n");
  }

  private static String fmt(double v) {
    return String.format(Locale.ROOT, "%.2f", v);
  }

  private static double parse(String s) {
    try {
      return Double.parseDouble(s);
    } catch (RuntimeException e) {
      return Double.NaN;
    }
  }

  private static String first(Map<String, String> row, String a, String b) {
    String v = row.get(a);
    return v == null || v.isEmpty() ? row.get(b) : v;
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }

  private static List<String> concat(List<String> a, List<String> b) {
    List<String> out = new ArrayList<>(a);
    out.addAll(b);
    return List.copyOf(out);
  }
}
