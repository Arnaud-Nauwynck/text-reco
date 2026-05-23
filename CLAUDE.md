# CLAUDE.md

## Project Overview

**text-reco** is a JavaFX desktop application for text recognition using OpenCV. 

- Java 21, maven, JavaFX 25-ea (controls, fxml, media), OpenCV 4.9.0-0 via `org.openpnp:opencv`

## Code Structure

All application code lives under `fr.an.textreco`.

```
src/main/java/fr/an/textreco/
└── AppMain.java   # Entry point (currently a stub)
├── model
│    └── FrameData.java
├── ui
│    └── CameraView.java
│    └── CameraService.java
├── processing
│    ├── FrameProcessor.java
│    ├── EdgeDetectorProcessor.java
│    └── ProcessingContext.java
└── util
     └── FxImageUtils.java
```

