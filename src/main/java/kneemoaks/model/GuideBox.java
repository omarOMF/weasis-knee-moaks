package kneemoaks.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The box drawn around a marked structure, like the boxes of a spine labeler: a block in patient
 * space centred on the marker and sized for the structure. No image analysis, so it is instant;
 * on any slice that cuts through the block it shows as the outline of that cut.
 *
 * <p>Patient axes are DICOM's: x to the patient's left, y to the back, z to the head.
 */
public final class GuideBox {

  /** Typical adult size of each structure, in mm: side to side, front to back, top to bottom. */
  public static double[] sizeMm(String structureKey) {
    if (structureKey == null) {
      return null;
    }
    return switch (structureKey) {
      case "FEM-M", "FEM-L" -> new double[] {30, 55, 35}; // femoral condyle
      case "TRO-M", "TRO-L" -> new double[] {25, 20, 30}; // trochlear facet
      case "PAT-M", "PAT-L" -> new double[] {22, 20, 40}; // patellar facet
      case "TIB-M", "TIB-L" -> new double[] {32, 45, 25}; // tibial plateau
      default -> null;
    };
  }

  private GuideBox() {}

  /**
   * Where a plane cuts the box: the corners of that cut, in order around it, in patient mm.
   * Empty when the plane misses the box.
   *
   * @param center box centre (the marker)
   * @param size box size along x, y, z
   * @param origin any point on the plane (e.g. the slice's top-left pixel)
   * @param normal the plane's normal
   */
  public static List<double[]> cut(double[] center, double[] size, double[] origin, double[] normal) {
    double[][] corners = new double[8][];
    int n = 0;
    for (int ix = -1; ix <= 1; ix += 2) {
      for (int iy = -1; iy <= 1; iy += 2) {
        for (int iz = -1; iz <= 1; iz += 2) {
          corners[n++] = new double[] {
            center[0] + ix * size[0] / 2, center[1] + iy * size[1] / 2, center[2] + iz * size[2] / 2
          };
        }
      }
    }
    int[][] edges = {
      {0, 1}, {2, 3}, {4, 5}, {6, 7}, // along z
      {0, 2}, {1, 3}, {4, 6}, {5, 7}, // along y
      {0, 4}, {1, 5}, {2, 6}, {3, 7} // along x
    };
    List<double[]> pts = new ArrayList<>();
    for (int[] e : edges) {
      double[] a = corners[e[0]];
      double[] b = corners[e[1]];
      double da = signedDistance(a, origin, normal);
      double db = signedDistance(b, origin, normal);
      if (da == 0 && db == 0) {
        continue; // edge lies in the plane: its ends are found by the edges that cross
      }
      if ((da <= 0 && db >= 0) || (da >= 0 && db <= 0)) {
        double t = da / (da - db);
        double[] p = {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), a[2] + t * (b[2] - a[2])};
        if (!contains(pts, p)) {
          pts.add(p);
        }
      }
    }
    if (pts.size() < 3) {
      return List.of();
    }
    return ordered(pts, normal);
  }

  /** Signed distance in mm from a point to the plane. */
  public static double signedDistance(double[] p, double[] origin, double[] normal) {
    double len = Math.sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2]);
    return ((p[0] - origin[0]) * normal[0] + (p[1] - origin[1]) * normal[1] + (p[2] - origin[2]) * normal[2]) / len;
  }

  private static boolean contains(List<double[]> pts, double[] p) {
    for (double[] q : pts) {
      if (Math.abs(q[0] - p[0]) < 1e-6 && Math.abs(q[1] - p[1]) < 1e-6 && Math.abs(q[2] - p[2]) < 1e-6) {
        return true;
      }
    }
    return false;
  }

  /** Puts the cut's corners in order around their centre, so they draw as a closed outline. */
  private static List<double[]> ordered(List<double[]> pts, double[] normal) {
    double[] c = new double[3];
    for (double[] p : pts) {
      c[0] += p[0] / pts.size();
      c[1] += p[1] / pts.size();
      c[2] += p[2] / pts.size();
    }
    // two directions in the plane
    double[] u = sub(pts.get(0), c);
    normalize(u);
    double[] v = cross(normal, u);
    normalize(v);
    List<double[]> out = new ArrayList<>(pts);
    out.sort((p, q) -> Double.compare(angle(p, c, u, v), angle(q, c, u, v)));
    return out;
  }

  private static double angle(double[] p, double[] c, double[] u, double[] v) {
    double[] d = sub(p, c);
    return Math.atan2(dot(d, v), dot(d, u));
  }

  private static double[] sub(double[] a, double[] b) {
    return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
  }

  private static double dot(double[] a, double[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }

  private static double[] cross(double[] a, double[] b) {
    return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
  }

  private static void normalize(double[] a) {
    double l = Math.sqrt(dot(a, a));
    if (l > 0) {
      a[0] /= l;
      a[1] /= l;
      a[2] /= l;
    }
  }
}
