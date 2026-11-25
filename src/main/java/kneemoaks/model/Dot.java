package kneemoaks.model;

import java.util.UUID;

/**
 * A dot the reader dropped on the MRI. Its position is kept in patient coordinates (mm), so it can
 * be shown on any view of the same frame of reference: axial, sagittal or coronal.
 *
 * @param location the MOAKS location key, or null while the reader has not said where it is
 */
public record Dot(
    String id,
    String location,
    double x,
    double y,
    double z,
    String frameOfReferenceUid,
    String seriesUid,
    String sopUid,
    double imageX,
    double imageY) {

  public static Dot create(
      double x,
      double y,
      double z,
      String frameOfReferenceUid,
      String seriesUid,
      String sopUid,
      double imageX,
      double imageY) {
    String id = UUID.randomUUID().toString().substring(0, 8);
    return new Dot(id, null, x, y, z, frameOfReferenceUid, seriesUid, sopUid, imageX, imageY);
  }

  public Dot withLocation(String key) {
    return new Dot(id, key, x, y, z, frameOfReferenceUid, seriesUid, sopUid, imageX, imageY);
  }

  public double[] position() {
    return new double[] {x, y, z};
  }
}
