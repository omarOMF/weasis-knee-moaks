package kneemoaks.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** The DICOM study being read, as far as the CSV needs it. Empty strings when unknown. */
public record StudyInfo(
    String patientId,
    String accessionNumber,
    String studyDate,
    String studyInstanceUid,
    String seriesInstanceUid,
    String seriesDescription,
    String laterality) {

  public static final StudyInfo NONE = new StudyInfo("", "", "", "", "", "", "");

  public StudyInfo {
    patientId = clean(patientId);
    accessionNumber = clean(accessionNumber);
    studyDate = clean(studyDate);
    studyInstanceUid = clean(studyInstanceUid);
    seriesInstanceUid = clean(seriesInstanceUid);
    seriesDescription = clean(seriesDescription);
    laterality = clean(laterality);
  }

  public boolean isEmpty() {
    return studyInstanceUid.isEmpty() && patientId.isEmpty() && seriesDescription.isEmpty();
  }

  /** Identifies the study: its UID, else patient and date, else the series description. */
  public String studyKey() {
    if (!studyInstanceUid.isEmpty()) {
      return studyInstanceUid;
    }
    if (!patientId.isEmpty()) {
      return patientId + "|" + studyDate;
    }
    return "series:" + seriesDescription;
  }

  private static final Pattern RIGHT =
      Pattern.compile("(^|[^A-Z])(R|RT|RIGHT|DX|DROIT[E]?|DERECH[AO])([^A-Z]|$)");
  private static final Pattern LEFT =
      Pattern.compile("(^|[^A-Z])(L|LT|LEFT|SIN|GAUCHE|IZQUIERD[AO])([^A-Z]|$)");

  /** "Right", "Left" or "" from the DICOM laterality, else from the series description. */
  public String guessKneeSide() {
    String lat = laterality.toUpperCase(Locale.ROOT);
    if (lat.startsWith("R")) {
      return "Right";
    }
    if (lat.startsWith("L")) {
      return "Left";
    }
    String d = seriesDescription.toUpperCase(Locale.ROOT).replace('_', ' ');
    boolean r = RIGHT.matcher(d).find();
    boolean l = LEFT.matcher(d).find();
    if (r != l) {
      return r ? "Right" : "Left";
    }
    return "";
  }

  private static String clean(String s) {
    return s == null ? "" : s.trim();
  }
}
