# CLAUDE.md

## Project Overview

**text-reco** is a JavaFX desktop application for real-time text recognition using OpenCV.
Goal: capture live camera frames, preprocess them (perspective, binaries, detect lines incrementally),
and extract text via OCR.
Focusing on fast terminal screen OCR, possibly scrolling fast and low resolution, but fixed font, and stable (pre-calibrated) image between frames.

- Java 21, Maven
- JavaFX 25-ea (controls, graphics)
- OpenCV 4.9.0-0 via `org.openpnp:opencv`
- Lombok for boilerplate reduction

code follow Oracle style & indentation.

IMPORTANT: DO NOT launch mvn or git tools to check compilation results (save tokens=cost), as the user will do it himself.

## Code Structure

The architecture should strictly follow the MVC separation: Model contains only data, View contains only UI components,
and Controller (Processing, CameraService) mediates between them.

```
src/main/java/fr/an/textreco/
 /Launcher.java  # Main entry point
 /model/*.java   model classes of the MVC pattern
 /ui/*.java   view classes of the MVC pattern
 /processing/*.java  OpenCV processing logic (using Mat, ..)
 /util/*.java 
```

