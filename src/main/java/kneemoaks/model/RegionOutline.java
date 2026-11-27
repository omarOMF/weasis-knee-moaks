package kneemoaks.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Smart outline": from the point the reader clicked, grows a region of similar signal on that
 * one slice, stopping at edges, and returns its outline. No AI; it works best when the structure
 * is clearly brighter or darker than what surrounds it, and it gives up (returns null) rather
 * than spill across the image.
 */
public final class RegionOutline {

  /** The outline in pixel coordinates of the slice (pixel centres at x+0.5, y+0.5). */
  public record Result(List<double[]> polygon, int areaPixels, double tolerance) {}

  private RegionOutline() {}

  /**
   * @param pixels row-major slice values (any scale)
   * @param maxRadiusPx how far from the seed the region may reach, in pixels
   */
  public static Result outline(float[] pixels, int w, int h, int seedX, int seedY, double maxRadiusPx) {
    if (w < 8 || h < 8 || seedX < 1 || seedY < 1 || seedX >= w - 1 || seedY >= h - 1) {
      return null;
    }
    float[] img = smooth(pixels, w, h);
    double range = robustRange(img);
    if (range <= 0) {
      return null;
    }
    double seedValue = mean5(img, w, h, seedX, seedY);
    double r2 = maxRadiusPx * maxRadiusPx;
    int minArea = 12;

    // Grow at a range of tolerances. The structure is the first size the region settles at
    // (it barely grows when the tolerance loosens); a later sudden jump would be the region
    // spilling through a gap into its neighbour, which this never reaches.
    double[] steps = {0.04, 0.06, 0.08, 0.11, 0.15, 0.20, 0.26, 0.33};
    boolean[][] masks = new boolean[steps.length][];
    int[] areas = new int[steps.length];
    for (int k = 0; k < steps.length; k++) {
      masks[k] = grow(img, w, h, seedX, seedY, seedValue, steps[k] * range, r2, new int[1]);
      areas[k] = count(masks[k]);
    }
    int pick = -1;
    double flattest = Double.MAX_VALUE;
    int flattestAt = -1;
    for (int k = 0; k < steps.length - 1; k++) {
      if (areas[k] < 50) {
        continue;
      }
      double ratio = areas[k + 1] / (double) areas[k];
      if (ratio < 1.12) {
        pick = k;
        break;
      }
      if (ratio < flattest) {
        flattest = ratio;
        flattestAt = k;
      }
    }
    if (pick < 0) {
      pick = flattestAt;
    }
    boolean[] best = pick >= 0 ? masks[pick] : null;
    double bestTol = pick >= 0 ? steps[pick] * range : 0;
    if (best == null || !standsOut(img, best, w, h, range, seedX, seedY, r2)) {
      return null;
    }
    best = close(best, w, h);
    best = fillHoles(best, w, h);
    List<double[]> contour = trace(best, w, h);
    if (contour.size() < 6) {
      return null;
    }
    List<double[]> simple = simplify(contour, 0.9);
    List<double[]> smooth = chaikin(chaikin(simple));
    return new Result(smooth, count(best), bestTol);
  }

  // ---- steps ----

  /** 3x3 mean, to take the edge off MRI noise. */
  static float[] smooth(float[] p, int w, int h) {
    float[] out = new float[p.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        double s = 0;
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
          int yy = y + dy;
          if (yy < 0 || yy >= h) {
            continue;
          }
          for (int dx = -1; dx <= 1; dx++) {
            int xx = x + dx;
            if (xx < 0 || xx >= w) {
              continue;
            }
            s += p[yy * w + xx];
            n++;
          }
        }
        out[y * w + x] = (float) (s / n);
      }
    }
    return out;
  }

  /** Spread of the slice's values between the 2nd and 98th percentiles. */
  static double robustRange(float[] img) {
    float[] c = img.clone();
    Arrays.sort(c);
    return c[(int) (c.length * 0.98)] - c[(int) (c.length * 0.02)];
  }

  private static double mean5(float[] img, int w, int h, int sx, int sy) {
    double s = 0;
    int n = 0;
    for (int dy = -2; dy <= 2; dy++) {
      for (int dx = -2; dx <= 2; dx++) {
        int x = sx + dx;
        int y = sy + dy;
        if (x >= 0 && y >= 0 && x < w && y < h) {
          s += img[y * w + x];
          n++;
        }
      }
    }
    return s / n;
  }

  /** 4-connected growth from the seed: similar value, inside the circle. */
  private static boolean[] grow(float[] img, int w, int h, int sx, int sy, double seedValue,
      double tol, double r2, int[] touchedEdge) {
    boolean[] mask = new boolean[w * h];
    ArrayDeque<Integer> q = new ArrayDeque<>();
    int start = sy * w + sx;
    mask[start] = true;
    q.add(start);
    int[] dx = {1, -1, 0, 0};
    int[] dy = {0, 0, 1, -1};
    while (!q.isEmpty()) {
      int i = q.poll();
      int x = i % w;
      int y = i / w;
      for (int k = 0; k < 4; k++) {
        int nx = x + dx[k];
        int ny = y + dy[k];
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
          continue;
        }
        int j = ny * w + nx;
        if (mask[j] || Math.abs(img[j] - seedValue) > tol) {
          continue;
        }
        double ddx = nx - sx;
        double ddy = ny - sy;
        if (ddx * ddx + ddy * ddy > r2) {
          touchedEdge[0]++;
          continue;
        }
        mask[j] = true;
        q.add(j);
      }
    }
    return mask;
  }

  /**
   * True when the region is clearly different from a thin band around it (inside the distance
   * limit). Without this, noise on an even background would be "outlined".
   */
  static boolean standsOut(float[] img, boolean[] mask, int w, int h, double range,
      int sx, int sy, double r2) {
    boolean[] band = dilate(dilate(dilate(mask, w, h), w, h), w, h);
    // Where the region was cut off by the distance limit there is no real edge: leave it out.
    for (int i = 0; i < band.length; i++) {
      double dx = i % w - sx;
      double dy = i / w - sy;
      if (dx * dx + dy * dy > r2 * 0.97) {
        band[i] = false;
      }
    }
    double sIn = 0;
    double sIn2 = 0;
    int nIn = 0;
    for (int i = 0; i < mask.length; i++) {
      if (mask[i]) {
        sIn += img[i];
        sIn2 += (double) img[i] * img[i];
        nIn++;
      }
    }
    if (nIn == 0) {
      return false;
    }
    double mIn = sIn / nIn;
    double sd = Math.sqrt(Math.max(0, sIn2 / nIn - mIn * mIn));
    // Average distance of the surroundings from the region's level: brighter on one side and
    // darker on the other must not cancel out.
    double sDiff = 0;
    int nOut = 0;
    for (int i = 0; i < mask.length; i++) {
      if (!mask[i] && band[i]) {
        sDiff += Math.abs(img[i] - mIn);
        nOut++;
      }
    }
    if (nOut == 0) {
      return false;
    }
    return sDiff / nOut >= Math.max(3 * sd, 0.12 * range);
  }

  private static int count(boolean[] m) {
    int n = 0;
    for (boolean b : m) {
      if (b) {
        n++;
      }
    }
    return n;
  }

  /** Morphological closing with a 3x3 square: joins small gaps along the edge. */
  static boolean[] close(boolean[] m, int w, int h) {
    return erode(dilate(m, w, h), w, h);
  }

  private static boolean[] dilate(boolean[] m, int w, int h) {
    boolean[] o = new boolean[m.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        boolean v = false;
        for (int dy = -1; dy <= 1 && !v; dy++) {
          for (int dx = -1; dx <= 1 && !v; dx++) {
            int xx = x + dx;
            int yy = y + dy;
            v = xx >= 0 && yy >= 0 && xx < w && yy < h && m[yy * w + xx];
          }
        }
        o[y * w + x] = v;
      }
    }
    return o;
  }

  private static boolean[] erode(boolean[] m, int w, int h) {
    boolean[] o = new boolean[m.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        boolean v = true;
        for (int dy = -1; dy <= 1 && v; dy++) {
          for (int dx = -1; dx <= 1 && v; dx++) {
            int xx = x + dx;
            int yy = y + dy;
            v = xx >= 0 && yy >= 0 && xx < w && yy < h && m[yy * w + xx];
          }
        }
        o[y * w + x] = v;
      }
    }
    return o;
  }

  /** Fills holes: everything not reachable from the border through background is inside. */
  static boolean[] fillHoles(boolean[] m, int w, int h) {
    boolean[] outside = new boolean[m.length];
    ArrayDeque<Integer> q = new ArrayDeque<>();
    for (int x = 0; x < w; x++) {
      seed(m, outside, q, x);
      seed(m, outside, q, (h - 1) * w + x);
    }
    for (int y = 0; y < h; y++) {
      seed(m, outside, q, y * w);
      seed(m, outside, q, y * w + w - 1);
    }
    while (!q.isEmpty()) {
      int i = q.poll();
      int x = i % w;
      int y = i / w;
      if (x > 0) {
        seed(m, outside, q, i - 1);
      }
      if (x < w - 1) {
        seed(m, outside, q, i + 1);
      }
      if (y > 0) {
        seed(m, outside, q, i - w);
      }
      if (y < h - 1) {
        seed(m, outside, q, i + w);
      }
    }
    boolean[] o = new boolean[m.length];
    for (int i = 0; i < m.length; i++) {
      o[i] = !outside[i];
    }
    return o;
  }

  private static void seed(boolean[] m, boolean[] outside, ArrayDeque<Integer> q, int i) {
    if (!m[i] && !outside[i]) {
      outside[i] = true;
      q.add(i);
    }
  }

  /** Moore-neighbour tracing of the outer boundary; points at pixel centres. */
  static List<double[]> trace(boolean[] m, int w, int h) {
    int start = -1;
    for (int i = 0; i < m.length && start < 0; i++) {
      if (m[i]) {
        start = i;
      }
    }
    List<double[]> pts = new ArrayList<>();
    if (start < 0) {
      return pts;
    }
    // neighbours clockwise starting west
    int[] nx = {-1, -1, 0, 1, 1, 1, 0, -1};
    int[] ny = {0, -1, -1, -1, 0, 1, 1, 1};
    int cx = start % w;
    int cy = start / w;
    int sx = cx;
    int sy = cy;
    int dir = 0; // we entered the start pixel moving east, so begin looking west
    int guard = 0;
    do {
      pts.add(new double[] {cx + 0.5, cy + 0.5});
      boolean moved = false;
      for (int k = 0; k < 8; k++) {
        int d = (dir + k) % 8;
        int x = cx + nx[d];
        int y = cy + ny[d];
        if (x >= 0 && y >= 0 && x < w && y < h && m[y * w + x]) {
          cx = x;
          cy = y;
          dir = (d + 6) % 8; // back up two steps to keep hugging the edge
          moved = true;
          break;
        }
      }
      if (!moved) {
        break; // a single pixel
      }
    } while ((cx != sx || cy != sy) && ++guard < 4 * m.length);
    return pts;
  }

  /** Douglas-Peucker on a closed polygon. */
  static List<double[]> simplify(List<double[]> pts, double eps) {
    if (pts.size() < 4) {
      return pts;
    }
    int far = 0;
    double best = -1;
    for (int i = 1; i < pts.size(); i++) {
      double d = dist2(pts.get(0), pts.get(i));
      if (d > best) {
        best = d;
        far = i;
      }
    }
    List<double[]> a = dp(pts.subList(0, far + 1), eps);
    List<double[]> b = new ArrayList<>(pts.subList(far, pts.size()));
    b.add(pts.get(0));
    b = dp(b, eps);
    List<double[]> out = new ArrayList<>(a.subList(0, a.size() - 1));
    out.addAll(b.subList(0, b.size() - 1));
    return out;
  }

  private static List<double[]> dp(List<double[]> pts, double eps) {
    if (pts.size() < 3) {
      return new ArrayList<>(pts);
    }
    double[] a = pts.get(0);
    double[] b = pts.get(pts.size() - 1);
    int idx = -1;
    double max = -1;
    for (int i = 1; i < pts.size() - 1; i++) {
      double d = segDist(pts.get(i), a, b);
      if (d > max) {
        max = d;
        idx = i;
      }
    }
    if (max <= eps) {
      List<double[]> out = new ArrayList<>();
      out.add(a);
      out.add(b);
      return out;
    }
    List<double[]> left = dp(pts.subList(0, idx + 1), eps);
    List<double[]> right = dp(pts.subList(idx, pts.size()), eps);
    List<double[]> out = new ArrayList<>(left.subList(0, left.size() - 1));
    out.addAll(right);
    return out;
  }

  /** Corner cutting: rounds the pixel staircase into a smooth line. */
  static List<double[]> chaikin(List<double[]> pts) {
    List<double[]> out = new ArrayList<>();
    int n = pts.size();
    for (int i = 0; i < n; i++) {
      double[] p = pts.get(i);
      double[] q = pts.get((i + 1) % n);
      out.add(new double[] {0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]});
      out.add(new double[] {0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]});
    }
    return out;
  }

  private static double dist2(double[] a, double[] b) {
    double dx = a[0] - b[0];
    double dy = a[1] - b[1];
    return dx * dx + dy * dy;
  }

  private static double segDist(double[] p, double[] a, double[] b) {
    double vx = b[0] - a[0];
    double vy = b[1] - a[1];
    double len2 = vx * vx + vy * vy;
    double t = len2 == 0 ? 0 : ((p[0] - a[0]) * vx + (p[1] - a[1]) * vy) / len2;
    t = Math.max(0, Math.min(1, t));
    double x = a[0] + t * vx - p[0];
    double y = a[1] + t * vy - p[1];
    return Math.sqrt(x * x + y * y);
  }

  /** Area inside a polygon (shoelace), in the polygon's units squared. */
  public static double polygonArea(List<double[]> poly) {
    double s = 0;
    for (int i = 0; i < poly.size(); i++) {
      double[] a = poly.get(i);
      double[] b = poly.get((i + 1) % poly.size());
      s += a[0] * b[1] - b[0] * a[1];
    }
    return Math.abs(s) / 2;
  }
}
