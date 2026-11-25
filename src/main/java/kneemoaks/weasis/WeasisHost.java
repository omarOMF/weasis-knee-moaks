package kneemoaks.weasis;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.Timer;
import kneemoaks.model.Dot;
import kneemoaks.model.StudyInfo;
import kneemoaks.ui.MoaksController;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.EventManager;

/**
 * The Weasis side of the plugin: finds the open DICOM views, adds a dot layer on each, follows the
 * study being read, and scrolls views to a dot.
 */
final class WeasisHost implements MoaksController.ViewerHost {

  private final Map<JComponent, DotOverlay> overlays = new WeakHashMap<>();
  private MoaksController ctl;
  private boolean dotMode;
  private final Timer watch;

  WeasisHost() {
    watch = new Timer(700, e -> tick());
  }

  void start(MoaksController controller) {
    this.ctl = controller;
    watch.start();
  }

  void stop() {
    watch.stop();
    for (DotOverlay o : new ArrayList<>(overlays.values())) {
      o.detach();
    }
    overlays.clear();
  }

  // ---- watching the viewers ----

  private void tick() {
    for (ViewCanvas<DicomImageElement> v : allViews()) {
      JComponent c = v.getJComponent();
      if (c != null && !overlays.containsKey(c)) {
        DotOverlay o = new DotOverlay(v, ctl);
        overlays.put(c, o);
        c.add(o);
        c.addKeyListener(o.keys);
        o.setBounds(0, 0, c.getWidth(), c.getHeight());
        c.addComponentListener(new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            o.setBounds(0, 0, c.getWidth(), c.getHeight());
          }
        });
        o.setDotMode(dotMode);
      }
    }
    DicomImageElement img = selectedImage();
    if (img != null) {
      StudyInfo info = studyOf(img);
      if (!info.isEmpty() && !info.studyKey().equals(ctl.sheet().study().studyKey())) {
        ctl.openStudy(info);
      }
    }
  }

  @SuppressWarnings("unchecked")
  static List<ViewCanvas<DicomImageElement>> allViews() {
    List<ViewCanvas<DicomImageElement>> out = new ArrayList<>();
    List<ViewerPlugin<?>> plugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (plugins) {
      for (ViewerPlugin<?> p : plugins) {
        if (p instanceof ImageViewerPlugin<?> ip) {
          for (ViewCanvas<?> v : ip.getImagePanels()) {
            if (v.getImage() instanceof DicomImageElement || v.getSeries() != null) {
              out.add((ViewCanvas<DicomImageElement>) v);
            }
          }
        }
      }
    }
    return out;
  }

  private static DicomImageElement selectedImage() {
    ImageViewerPlugin<DicomImageElement> c = EventManager.getInstance().getSelectedView2dContainer();
    if (c == null) {
      return null;
    }
    ViewCanvas<DicomImageElement> v = c.getSelectedViewCanvas();
    return v == null ? null : v.getImage();
  }

  static StudyInfo studyOf(DicomImageElement img) {
    String lat = text(img, Tag.Laterality);
    if (lat.isEmpty()) {
      lat = text(img, Tag.ImageLaterality);
    }
    return new StudyInfo(
        text(img, Tag.PatientID),
        text(img, Tag.AccessionNumber),
        text(img, Tag.StudyDate),
        text(img, Tag.StudyInstanceUID),
        text(img, Tag.SeriesInstanceUID),
        text(img, Tag.SeriesDescription),
        lat);
  }

  static String text(DicomImageElement img, int tag) {
    Object v = TagD.getTagValue(img, tag);
    if (v == null && img.getMediaReader() instanceof DcmMediaReader reader) {
      // Some tags live at the study or patient level in Weasis: read them from the file header.
      Attributes dcm = reader.getDicomObject();
      String raw = dcm == null ? null : dcm.getString(tag);
      return raw == null ? "" : raw.trim();
    }
    if (v == null) {
      return "";
    }
    if (v instanceof LocalDate d) {
      return d.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
    if (v instanceof TemporalAccessor t && tag == Tag.StudyDate) {
      return DateTimeFormatter.BASIC_ISO_DATE.format(t);
    }
    if (v instanceof String[] arr) {
      return arr.length == 0 ? "" : arr[0];
    }
    return v.toString();
  }

  // ---- ViewerHost ----

  @Override
  public void setDotMode(boolean on) {
    dotMode = on;
    for (DotOverlay o : overlays.values()) {
      o.setDotMode(on);
    }
  }

  @Override
  public void showPanel() {
    KneeMoaksTool t = KneeMoaks.tool();
    if (t != null) {
      t.bringToFront();
    }
  }

  @Override
  public void refreshViews() {
    for (JComponent c : overlays.keySet()) {
      c.repaint();
    }
  }

  @Override
  public String describe(Dot dot) {
    for (ViewCanvas<DicomImageElement> v : allViews()) {
      MediaSeries<DicomImageElement> s = v.getSeries();
      DicomImageElement img = v.getImage();
      if (s == null || img == null || !dot.seriesUid().equals(text(img, Tag.SeriesInstanceUID))) {
        continue;
      }
      List<DicomImageElement> sorted = s.getSortedMedias(v.getCurrentSortComparator());
      for (int i = 0; i < sorted.size(); i++) {
        if (dot.sopUid().equals(text(sorted.get(i), Tag.SOPInstanceUID))) {
          return planeName(sorted.get(i).getSliceGeometry()) + " " + (i + 1);
        }
      }
    }
    return "";
  }

  static String planeName(GeometryOfSlice g) {
    if (g == null) {
      return "Im";
    }
    Vector3d n = g.getNormal();
    double x = Math.abs(n.x);
    double y = Math.abs(n.y);
    double z = Math.abs(n.z);
    if (x >= y && x >= z) {
      return "Sag";
    }
    return y >= z ? "Cor" : "Ax";
  }

  /** Scrolls each view of the selected viewer to the slice nearest the dot. */
  @Override
  public void goTo(Dot dot) {
    ImageViewerPlugin<DicomImageElement> c = EventManager.getInstance().getSelectedView2dContainer();
    if (c == null) {
      return;
    }
    ViewCanvas<DicomImageElement> original = c.getSelectedViewCanvas();
    ViewCanvas<DicomImageElement> home = null;
    Vector3d p = new Vector3d(dot.x(), dot.y(), dot.z());
    for (ViewCanvas<DicomImageElement> v : c.getImagePanels()) {
      MediaSeries<DicomImageElement> s = v.getSeries();
      if (s == null) {
        continue;
      }
      List<DicomImageElement> sorted = s.getSortedMedias(v.getCurrentSortComparator());
      int best = -1;
      double bestDist = Double.MAX_VALUE;
      for (int i = 0; i < sorted.size(); i++) {
        DicomImageElement img = sorted.get(i);
        if (!DotOverlay.sameFrame(dot, img)) {
          continue;
        }
        GeometryOfSlice g = img.getSliceGeometry();
        if (g == null) {
          continue;
        }
        double d = Math.abs(DotOverlay.distanceToPlane(p, g));
        if (d < bestDist) {
          bestDist = d;
          best = i;
        }
      }
      if (best < 0 || bestDist > 20) {
        continue;
      }
      if (dot.seriesUid().equals(text(sorted.get(best), Tag.SeriesInstanceUID))) {
        home = v;
      }
      c.setSelectedImagePane(v);
      int index = best;
      Optional<SliderCineListener> scroll = EventManager.getInstance().getAction(ActionW.SCROLL_SERIES);
      scroll.ifPresent(a -> a.setSliderValue(index + 1));
    }
    ViewCanvas<DicomImageElement> back = home != null ? home : original;
    if (back != null) {
      c.setSelectedImagePane(back);
    }
    refreshViews();
  }
}
