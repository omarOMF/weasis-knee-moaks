Knee MOAKS for Weasis 4.8.1
===========================

Install (once)
1. Quit Weasis.
2. Right-click "Install-Knee-MOAKS.command" and choose Open, then click Open again.
   If macOS refuses ("unidentified developer"), either allow it in System Settings >
   Privacy & Security > Open Anyway, or open Terminal and run:
     bash ~/Downloads/weasis-knee-moaks/Install-Knee-MOAKS.command
3. Weasis opens with the plugin. The "Knee MOAKS" panel is in the right-hand tool area of the
   DICOM viewer, and a "MOAKS dot" button is in the toolbar.

Next time, open Weasis with:
   ~/Weasis-plugins/knee-moaks/Open Weasis with Knee MOAKS.command
(Weasis opened another way starts without the plugin.)

Scoring
- Click a subregion on the knee drawing, then tap its scores. Typing 21 sets cartilage 2.1.
- Dots: press "MOAKS dot" and click the finding on any image. A card asks where it is; pick
  the subregion and score it right there. Or choose a subregion in the panel and press
  "Add dot", then click the image.
- Dots show on every view of the study (sagittal, coronal, axial). Off the current slice they
  are drawn faded with their distance in mm. "Go to" scrolls the views to a dot.
- Anything you do not touch is saved as normal when you press "Finish knee".

Files (Documents/KneeMOAKS, change with "Folder..." in the panel)
- knee_moaks_long.csv   one row per scored field
- knee_moaks_wide.csv   one row per knee read
- knee_moaks_dots.csv   one row per dot, position in patient mm
Rows are kept per study, knee, visit and reader; saving again replaces that read's rows.
