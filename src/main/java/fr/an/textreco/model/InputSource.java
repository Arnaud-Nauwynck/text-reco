package fr.an.textreco.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.opencv.core.Mat;

/**
 * Mutable input-source state shared between the FX thread (writes) and the
 * camera loop (reads). Volatile fields and synchronized Mat access ensure
 * thread-safety without heavy locking in the hot loop.
 */
public class InputSource {

    /** Camera device index used when no file is loaded. */
    public volatile int cameraIndex = 0;

    /**
     * Whether the loop should reprocess the last frame instead of grabbing a new one.
     * BooleanProperty so the FX UI can bind to it directly.
     * The background thread reads it via isFrozen() which delegates to the property's get(),
     * which is a volatile read under the hood.
     */
    private final BooleanProperty frozen = new SimpleBooleanProperty(false);

    public BooleanProperty frozenProperty() { return frozen; }
    public boolean isFrozen()               { return frozen.get(); }
    public void    setFrozen(boolean v)     { frozen.set(v); }

    /**
     * When non-null the loop copies this Mat instead of reading from the camera.
     * Written by the FX thread; read by the camera thread. Access protected by synchronized(this).
     */
    private Mat loadedMat = null;

    public synchronized void setLoadedMat(Mat mat) {
        if (loadedMat != null) loadedMat.release();
        loadedMat = mat;
    }

    /** Atomically returns a clone of the loaded Mat and clears it (one-shot consume). */
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
