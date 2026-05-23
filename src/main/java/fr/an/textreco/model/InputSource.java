package fr.an.textreco.model;

import org.opencv.core.Mat;

/**
 * Holds the mutable input-source state read by the camera loop on every iteration.
 * All fields are volatile so the FX thread can write them safely.
 */
public class InputSource {

    /** camera device index used when no file is loaded */
    public volatile int cameraIndex = 0;

    /**
     * When non-null the loop copies this mat instead of reading from the camera.
     * Written by the FX thread; read by the camera thread.
     * Access is protected by synchronized(this).
     */
    private Mat loadedMat = null;

    /** when true the loop reprocesses the last captured frame instead of grabbing a new one */
    public volatile boolean frozen = false;

    public synchronized void setLoadedMat(Mat mat) {
        if (loadedMat != null) loadedMat.release();
        loadedMat = mat;
    }

    /** Returns a clone of the loaded mat so the caller owns it, or null if none loaded. */
    public synchronized Mat cloneLoadedMat() {
        return loadedMat != null ? loadedMat.clone() : null;
    }

    /** Atomically returns a clone of the loaded mat and clears it (one-shot consume). */
    public synchronized Mat cloneAndClearLoadedMat() {
        if (loadedMat == null) return null;
        Mat clone = loadedMat.clone();
        loadedMat.release();
        loadedMat = null;
        return clone;
    }

    public synchronized boolean hasLoadedMat() {
        return loadedMat != null;
    }

    public synchronized void release() {
        if (loadedMat != null) { loadedMat.release(); loadedMat = null; }
    }
}
