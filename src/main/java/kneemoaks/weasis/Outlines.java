package kneemoaks.weasis;

import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kneemoaks.model.Dot;
import kneemoaks.model.RegionOutline;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;

/**
 * Smart outlines of markers, worked out once from the slice the marker was placed on and kept in
 * that slice's image coordinates.
 */
final class Outlines {

  /** How far from the click an outline may reach. */
  static final double MAX_RADIUS_MM = 30;

  /** An outline in the image's model coordinates, or none (null path) when it gave up. */
  record Outline(String sopUid, Path2D.Double path) {}

  private static final Map<String, Outline> CACHE = new ConcurrentHashMap<>();

  private Outlines() {}

  /** The outline of a marker on this image, computing it the first time; null if none. */
  static Path2D.Double of(Dot d, DicomImageElement img, String sopUid) {
    Outline o = CACHE.get(d.id());
    if (o == null || !o.sopUid().equals(sopUid)) {
      o = new Outline(sopUid, compute(d, img));
      CACHE.put(d.id(), o);
    }
    return o.path();
  }

  static void forget(String markerId) {
    CACHE.remove(markerId);
  }

  private static Path2D.Double compute(Dot d, DicomImageElement img) {
    try {
      PlanarImage pi = img.getImage();
      if (pi == null) {
        return null;
      }
      Mat src = pi.toMat();
      Mat gray = src;
      if (src.channels() > 1) {
        gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);
      }
      Mat f = new Mat();
      gray.convertTo(f, CvType.CV_32F);
      int w = f.cols();
      int h = f.rows();
      float[] px = new float[w * h];
      f.get(0, 0, px);
      f.release();
      if (gray != src) {
        gray.release();
      }
      double rx = img.getRescaleX();
      double ry = img.getRescaleY();
      int sx = (int) Math.floor(d.imageX() / rx);
      int sy = (int) Math.floor(d.imageY() / ry);
      double pixelMm = img.getPixelSize();
      double radiusPx = pixelMm > 0 && pixelMm < 5 ? MAX_RADIUS_MM / pixelMm : 80;
      RegionOutline.Result r = RegionOutline.outline(px, w, h, sx, sy, radiusPx);
      if (r == null) {
        return null;
      }
      Path2D.Double path = new Path2D.Double();
      List<double[]> poly = r.polygon();
      for (int i = 0; i < poly.size(); i++) {
        double x = poly.get(i)[0] * rx;
        double y = poly.get(i)[1] * ry;
        if (i == 0) {
          path.moveTo(x, y);
        } else {
          path.lineTo(x, y);
        }
      }
      path.closePath();
      return path;
    } catch (RuntimeException | UnsatisfiedLinkError e) {
      return null; // never let an outline break the viewer
    }
  }
}
