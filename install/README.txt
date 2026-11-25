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

To update: download the new zip and run Install-Knee-MOAKS.command again. The build number
shows at the bottom of the panel.

Next time, open Weasis with:
   ~/Weasis-plugins/knee-moaks/Open Weasis with Knee MOAKS.command
(Weasis opened another way starts without the plugin.)

Scoring
- While reading: press "MOAKS dot", click the finding on the image, and pick one of eight
  structures (patella, trochlea, femur, tibia; medial or lateral). That's all; keep reading.
- To score: double-click a dot on the image (or click a structure in the panel). The panel
  comes to the front, open on the right. "Clear" empties everything scored in that structure. The panel shows everything
  under that structure: cartilage, bone marrow lesions, osteophytes, and for the tibia the
  meniscus and extrusion. "Rest normal" marks everything you did not score there as normal.
- Soft tissue (effusion, Hoffa, ligaments, cysts and bursae, loose bodies) is under "Whole knee".
- Delete a dot: click it and press Delete; or right-click (two-finger / Control-click) it and
  choose Delete dot; or press the red Delete next to it in the panel. "Hide dots" (bottom of
  the panel) hides them all; the right-click menu can also delete all dots on the knee.
- Dots show on every view of the study. Off the current slice they are faded, with their
  distance in mm. "Go to" next to a dot scrolls the views to it.
- Anything not scored is saved as normal when you press "Finish knee".

Files (in your home folder: Finder > Go > Home > KneeMOAKS; "Files" at the bottom of the panel
opens the folder, right-click it to choose another)
- knee_moaks_long.csv   one row per scored field
- knee_moaks_wide.csv   one row per knee read
- knee_moaks_dots.csv   one row per dot, position in patient mm
Rows are kept per study, knee, visit and reader; saving again replaces that read's rows.
