package fr.an.textreco.processing;

import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.model.InputSource;
import fr.an.textreco.util.FxImageUtils.ImageBuffer;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the VideoCapture device, frame acquisition, and raw-frame image conversion.
 * All OpenCV resources (VideoCapture, Mats, ImageBuffer) live here.
 */
public class CameraCapture {

    private final InputSource inputSource;

    // owned resources — allocated in open(), released in release()
    private VideoCapture capture;
    private final Mat raw         = new Mat();
    private final Mat snapshot    = new Mat();
    private final ImageBuffer rawBuf = new ImageBuffer();

    private volatile boolean pendingCameraChange = false;

    public CameraCapture(InputSource inputSource) {
        this.inputSource = inputSource;
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    public void open() {
        capture = new VideoCapture(inputSource.cameraIndex, Videoio.CAP_DSHOW);
    }

    public void release() {
        if (capture != null) capture.release();
        raw.release();
        snapshot.release();
        rawBuf.release();
    }

    // -------------------------------------------------------------------------
    // per-frame acquisition (camera thread)
    // -------------------------------------------------------------------------

    /**
     * Acquires the next frame into the internal raw Mat.
     * Returns true if a frame is ready, false if the caller should skip this iteration.
     */
    public boolean readFrame() throws InterruptedException {
        if (pendingCameraChange) {
            pendingCameraChange = false;
            capture.release();
            capture = new VideoCapture(inputSource.cameraIndex, Videoio.CAP_DSHOW);
        }

        Mat loaded = inputSource.cloneAndClearLoadedMat();
        if (loaded != null) {
            loaded.copyTo(raw);
            loaded.release();
            raw.copyTo(snapshot);
            return true;
        }
        if (inputSource.isFrozen()) {
            if (raw.empty()) { Thread.sleep(20); return false; }
            return true;
        }
        capture.read(raw);
        if (raw.empty()) { Thread.sleep(5); return false; }
        raw.copyTo(snapshot);
        return true;
    }

    /** Returns the current raw Mat (valid until the next readFrame() call). */
    public Mat getRaw() { return raw; }

    /** Converts the raw frame to a WritableImage (BGR→RGB). */
    public WritableImage toWritableImage() {
        return rawBuf.update(raw);
    }

    // -------------------------------------------------------------------------
    // controls (FX thread)
    // -------------------------------------------------------------------------

    public void selectCamera(int index) {
        inputSource.cameraIndex = index;
        inputSource.setLoadedMat(null);
        pendingCameraChange = true;
    }

    public void saveSnapshot(File file) {
        if (!snapshot.empty()) Imgcodecs.imwrite(file.getAbsolutePath(), snapshot);
    }

    // -------------------------------------------------------------------------
    // static probe utility
    // -------------------------------------------------------------------------

    /**
     * Probes indices 0..maxIndex via CAP_DSHOW with a per-camera timeout.
     * Must be called from a background thread — VideoCapture() blocks on Windows
     * for several seconds when an index has no device.
     */
    public static List<CameraDevice> probeAvailableCameras(int maxIndex, long timeoutMs) {
        List<CameraDevice> devices = new ArrayList<>();
        for (int i = 0; i <= maxIndex; i++) {
            final int idx = i;
            CameraDevice[] holder = new CameraDevice[1];
            Thread t = new Thread(() -> {
                VideoCapture vc = new VideoCapture(idx, Videoio.CAP_DSHOW);
                if (vc.isOpened()) {
                    int w      = (int) vc.get(Videoio.CAP_PROP_FRAME_WIDTH);
                    int h      = (int) vc.get(Videoio.CAP_PROP_FRAME_HEIGHT);
                    double fps = vc.get(Videoio.CAP_PROP_FPS);
                    vc.release();
                    holder[0] = new CameraDevice(idx, "Camera " + idx, w, h, fps);
                } else {
                    vc.release();
                }
            });
            t.setDaemon(true);
            t.start();
            try { t.join(timeoutMs); } catch (InterruptedException ignored) {}
            if (holder[0] != null) {
                devices.add(holder[0]);
            } else {
                t.interrupt();
                break;
            }
        }
        return devices;
    }
}
