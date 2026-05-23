# CLAUDE.md

## Project Overview

**text-reco** is a JavaFX desktop application for real-time text recognition using OpenCV.  
Goal: capture live camera frames, preprocess them (perspective, binaries, detect lines incrementally),
and extract text via OCR.

- Java 21, Maven
- JavaFX 25-ea (controls, graphics)
- OpenCV 4.9.0-0 via `org.openpnp:opencv`
- Lombok for boilerplate reduction

code follow Oracle style & indentation.

## Code Structure

All application code lives under `fr.an.textreco`.

The architecture should strictly follow the MVCseparation: Model contains only data, View contains only UI components,
and Controller (Processing, CameraService) mediates between them.

```
src/main/java/fr/an/textreco/
├── Launcher.java  # Main entry point — calls TextRecoJavaFxApplication.launch()
├── model/
│   └── *.java   model classes of the MVC pattern
├── ui/
│   └── *.java   view classes of the MVC pattern
├── processing/
│   └──  *.java  OpenCV processing logic (using Mat, ..)
└── util/
    └── *.java 
```

## Key Conventions

- Camera capture and processing run on a **daemon background thread**; UI updates always via `Platform.runLater`.
- `FrameData` and `ProcessingContext` mats are **reused across frames** — never create new Mat per frame in the hot loop.

## Next Steps / TODOs

- Wire OCR engine (e.g. Tesseract via `tess4j`) and push results to `ResultsTab.appendText()`
- Honour `SettingsTab` camera index / resolution changes by restarting `CameraService`
- Add more `FrameProcessor` implementations (adaptive threshold, morphological ops, perspective correction)
