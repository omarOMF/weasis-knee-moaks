# Knee MOAKS for Weasis

A Weasis 4.8.1 plugin for scoring knee MRI with MOAKS (Hunter et al., Osteoarthritis and Cartilage 2011).

- **Knee MOAKS panel** in the DICOM viewer's tool area: a shaded knee illustration (joint surfaces opened out, menisci on the tibial plateau, a side view for soft tissue) to pick a subregion and score it.
- **Full MOAKS**: cartilage (area.full-thickness), bone marrow lesions (size, % lesion, number, including the subspinous region), osteophytes, menisci (morphology and extrusion), effusion- and Hoffa-synovitis, ACL, PCL, patellar tendon, periarticular cysts and bursae, loose bodies.
- **MOAKS markers** on the images: click the finding and pick one of eight structures (patella, trochlea, femur, tibia; medial or lateral). Double-click a marker later to score everything under that structure in the panel. Markers are small open crosshairs kept in patient coordinates and shown on sagittal, coronal and axial views.
- **CSV output** in `~/KneeMOAKS`: long (one row per field), wide (one row per read) and markers files. Untouched items are saved as normal when the knee is finished.

## Install

Download `weasis-knee-moaks.zip` from the [latest release](../../releases/tag/latest), unzip it and follow `README.txt` inside.

## Build

GitHub Actions builds the plugin on every push (`.github/workflows/build.yml`): it checks out Weasis 4.8.1 next to this repository, builds the Weasis modules the plugin uses with JDK 25, runs the model checks, draws panel previews and packages the bundle.

To build locally, with JDK 25 and Maven:

```bash
git clone --branch v4.8.1 --depth 1 https://github.com/nroduit/Weasis weasis
(cd weasis && mvn -ntp install -DskipTests -Drevision=4.8.1 -Dchangelist= -pl weasis-dicom/weasis-dicom-viewer2d -am)
cd weasis-knee-moaks && mvn -ntp package -Drevision=4.8.1 -Dchangelist=
```

## Code

- `kneemoaks.model`: the MOAKS sheet, scores, dots, CSV files (no Weasis dependency).
- `kneemoaks.ui`: the Swing panel, illustrations and the dot card.
- `kneemoaks.weasis`: the Weasis tool, toolbar and the dot layer on each view.
