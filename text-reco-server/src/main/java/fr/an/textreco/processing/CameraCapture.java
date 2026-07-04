package fr.an.textreco.processing;

import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.model.InputSource;
import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CameraCapture {

    private final InputSource inputSource;

    private VideoCapture capture;
    private final Mat raw = MatFacade.alloc("CameraCapture.raw");
    private final Mat snapshot = MatFacade.alloc("CameraCapture.snapshot");

    private volatile boolean pendingCameraChange = false;

    public CameraCapture(InputSource inputSource) {
        this.inputSource = inputSource;
    }

    public void open() {
        capture = new VideoCapture(inputSource.cameraIndex, Videoio.CAP_DSHOW);
    }

    public void release() {
        if (capture != null) capture.release();
        MatFacade.release(raw, "CameraCapture.raw");
        MatFacade.release(snapshot, "CameraCapture.snapshot");
    }

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
            if (raw.empty()) {
                Thread.sleep(20);
                return false;
            }
            return true;
        }
        capture.read(raw);
        if (raw.empty()) {
            Thread.sleep(5);
            return false;
        }
        raw.copyTo(snapshot);
        return true;
    }

    public Mat getRaw() {
        return raw;
    }

    public void selectCamera(int index) {
        inputSource.cameraIndex = index;
        inputSource.setLoadedMat(null);
        pendingCameraChange = true;
    }

    public void saveSnapshot(File file) {
        if (!snapshot.empty()) Imgcodecs.imwrite(file.getAbsolutePath(), snapshot);
    }

    public static List<CameraDevice> probeAvailableCameras(int maxIndex, long timeoutMs) {
        List<CameraDevice> devices = new ArrayList<>();
        for (int i = 0; i <= maxIndex; i++) {
            final int idx = i;
            CameraDevice[] holder = new CameraDevice[1];
            Thread t = new Thread(() -> {
                VideoCapture vc = new VideoCapture(idx, Videoio.CAP_DSHOW);
                if (vc.isOpened()) {
                    int w = (int) vc.get(Videoio.CAP_PROP_FRAME_WIDTH);
                    int h = (int) vc.get(Videoio.CAP_PROP_FRAME_HEIGHT);
                    double fps = vc.get(Videoio.CAP_PROP_FPS);
                    vc.release();
                    holder[0] = new CameraDevice(idx, "Camera " + idx, w, h, fps);
                } else {
                    vc.release();
                }
            });
            t.setDaemon(true);
            t.start();
            try {
                t.join(timeoutMs);
            } catch (InterruptedException ignored) {
            }
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
