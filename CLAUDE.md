# CLAUDE.md

## Project Overview

**text-reco** is a JavaFX desktop application for real-time text recognition using OpenCV.  
Goal: capture live camera frames, preprocess them (edge detection, thresholding), and extract text via OCR.

- Java 21, Maven
- JavaFX 25-ea (controls, graphics)
- OpenCV 4.9.0-0 via `org.openpnp:opencv`
- Lombok for boilerplate reduction

## Code Structure

All application code lives under `fr.an.textreco`.

```
src/main/java/fr/an/textreco/
├── Launcher.java                  # Main entry point — calls TextRecoJavaFxApplication.launch()
├── model/
│   └── FrameData.java             # Holds raw/gray/processed OpenCV Mat per frame
├── processing/
│   ├── FrameProcessor.java        # Interface: process(FrameData, ProcessingContext)
│   ├── EdgeDetectorProcessor.java # Canny edge detection; exposes cannyThreshold1/2 (volatile, live-tunable)
│   └── ProcessingContext.java     # Scratch Mat buffers reused across frames
├── ui/
│   ├── TextRecoJavaFxApplication.java  # JavaFX Application; wires CameraService → TextRecoView
│   ├── CameraService.java              # Background thread: VideoCapture → process → publish rawImage + processedImage properties
│   ├── TextRecoView.java               # Root view; TabPane wrapping all four tabs
│   └── tab/
│       ├── CameraTab.java          # Side-by-side raw and processed ImageViews
│       ├── ProcessingTab.java      # Canny threshold sliders + live FPS / resolution stats
│       ├── SettingsTab.java        # Camera index, capture resolution spinners
│       └── ResultsTab.java         # TextArea for recognised text output; appendText() / setText() API
└── util/
    └── FxImageUtils.java           # matToJavaFXWritableImage(): BGR Mat → WritableImage (BGR→RGB + PixelWriter)
```

## Architecture

```
VideoCapture (background thread)
  └── FrameData.raw  ──► EdgeDetectorProcessor ──► FrameData.processed
                                │
                    FxImageUtils.matToJavaFXWritableImage()
                                │
              CameraService: rawImageProperty / processedImageProperty
                                │  (Platform.runLater)
                         TextRecoView
                           ├── CameraTab      (ImageViews)
                           ├── ProcessingTab  (sliders + stats)
                           ├── SettingsTab    (camera config)
                           └── ResultsTab     (OCR output)
```

## Key Conventions

- Camera capture and processing run on a **daemon background thread**; UI updates always via `Platform.runLater`.
- `FrameData` and `ProcessingContext` mats are **reused across frames** — never create new Mat per frame in the hot loop.
- `EdgeDetectorProcessor` thresholds are `volatile` so the FX thread can write them while the camera thread reads them without synchronisation overhead.
- `FxImageUtils.matToJavaFXWritableImage` expects a **3-channel BGR** mat; single-channel output must be converted to BGR first (`COLOR_GRAY2BGR`).

## Next Steps / TODOs

- Wire OCR engine (e.g. Tesseract via `tess4j`) and push results to `ResultsTab.appendText()`
- Honour `SettingsTab` camera index / resolution changes by restarting `CameraService`
- Add more `FrameProcessor` implementations (adaptive threshold, morphological ops, perspective correction)
