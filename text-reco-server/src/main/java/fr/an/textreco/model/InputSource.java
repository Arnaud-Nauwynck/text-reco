package fr.an.textreco.model;

import org.opencv.core.Mat;

/**
 * Mutable input-source state shared between the REST thread (writes) and the
 * pipeline loop (reads). Volatile fields and synchronized Mat access ensure
 * thread-safety without heavy locking in the hot loop.
 */
public class InputSource {

    public volatile int cameraIndex = 0;

    private volatile boolean frozen = false;

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean v) {
        frozen = v;
    }

    private Mat loadedMat = null;

    public synchronized void setLoadedMat(Mat mat) {
        if (loadedMat != null) {
            loadedMat.release();
        }
        this.loadedMat = mat;
    }

    public synchronized Mat cloneAndClearLoadedMat() {
        if (loadedMat == null) {
            return null;
        }
        Mat clone = loadedMat.clone();
        loadedMat.release();
        this.loadedMat = null;
        return clone;
    }

    public synchronized boolean hasLoadedMat() {
        return loadedMat != null;
    }

    public synchronized void release() {
        if (loadedMat != null) {
            loadedMat.release();
            this.loadedMat = null;
        }
    }
}
