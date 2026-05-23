package fr.an.textreco.ui;

import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.model.FrameData;
import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.InputSource;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.FrameProcessor;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.ProcessingContext;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.util.FxImageUtils.ImageBuffer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.WritableImage;
import lombok.Getter;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CameraService {

    @Getter
    private final ObjectProperty<WritableImage>            rawImageProperty         = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<WritableImage>            processedImageProperty   = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<WritableImage>            perspectiveImageProperty = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<PreProcessingResult>      preProcessingProperty    = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<TextLineExtractionResult> textLinesProperty        = new SimpleObjectProperty<>();
    @Getter
    private final ObjectProperty<FrameStats>               frameStatsProperty       = new SimpleObjectProperty<>();
    @Getter
    private final BooleanProperty                          frozenProperty           = new SimpleBooleanProperty(false);

    @Getter
    private final InputSource inputSource = new InputSource();

    private final FrameProcessor processor;
    private final PerspectiveTransformProcessor perspectiveProcessor;
    private final PreProcessingProcessor preProcessingProcessor;
    private final TextLineExtractorProcessor lineExtractor;

    private volatile boolean running;
    private Thread worker;

    public CameraService(FrameProcessor processor,
                         PerspectiveTransformProcessor perspectiveProcessor,
                         PreProcessingProcessor preProcessingProcessor,
                         TextLineExtractorProcessor lineExtractor) {
        this.processor = processor;
        this.perspectiveProcessor = perspectiveProcessor;
        this.preProcessingProcessor = preProcessingProcessor;
        this.lineExtractor = lineExtractor;
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    public void start() {
        running = true;
        worker = new Thread(this::runLoop);
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        try { worker.join(); } catch (InterruptedException ignored) {}
    }

    // -------------------------------------------------------------------------
    // input controls (FX thread)
    // -------------------------------------------------------------------------

    public void toggleFreeze() {
        boolean next = !inputSource.frozen;
        inputSource.frozen = next;
        frozenProperty.set(next);
    }

    public void loadImageFile(File file) {
        Mat mat = Imgcodecs.imread(file.getAbsolutePath());
        if (mat.empty()) { System.err.println("Could not load image: " + file); return; }
        inputSource.setLoadedMat(mat);
        inputSource.frozen = true;
        frozenProperty.set(true);
    }

    public void saveRawImage(File file) {
        Mat snapshot = lastRawSnapshot;
        if (snapshot == null || snapshot.empty()) return;
        Imgcodecs.imwrite(file.getAbsolutePath(), snapshot);
    }

    public void selectCamera(int index) {
        inputSource.cameraIndex = index;
        inputSource.setLoadedMat(null);
        inputSource.frozen = false;
        frozenProperty.set(false);
        pendingCameraChange = true;
    }

    /**
     * Probes indices 0..maxIndex using DirectShow (CAP_DSHOW) with a per-camera timeout.
     * Reads width, height and fps from each opened capture.
     * Must be called from a background thread — VideoCapture() can block for seconds
     * on Windows when the index does not exist.
     */
    public static List<CameraDevice> probeAvailableCameras(int maxIndex, long timeoutMs) {
        List<CameraDevice> devices = new ArrayList<>();
        for (int i = 0; i <= maxIndex; i++) {
            final int idx = i;
            CameraDevice[] holder = new CameraDevice[1];
            Thread t = new Thread(() -> {
                VideoCapture vc = new VideoCapture(idx, Videoio.CAP_DSHOW);
                if (vc.isOpened()) {
                    int w   = (int) vc.get(Videoio.CAP_PROP_FRAME_WIDTH);
                    int h   = (int) vc.get(Videoio.CAP_PROP_FRAME_HEIGHT);
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
                t.interrupt();  // best-effort; native thread may not respond
                break;          // stop probing — higher indices unlikely to exist
            }
        }
        return devices;
    }

    // -------------------------------------------------------------------------
    // loop
    // -------------------------------------------------------------------------

    private volatile Mat lastRawSnapshot = null;
    private volatile boolean pendingCameraChange = false;

    private void runLoop() {
        VideoCapture capture = new VideoCapture(inputSource.cameraIndex, Videoio.CAP_DSHOW);
        if (!capture.isOpened() && !inputSource.hasLoadedMat()) {
            System.err.println("Cannot open camera");
            return;
        }

        FrameData frame            = new FrameData();
        FrameData perspectiveFrame = new FrameData();
        ProcessingContext context            = new ProcessingContext();
        ProcessingContext perspectiveContext = new ProcessingContext();

        ImageBuffer rawBuf         = new ImageBuffer();
        ImageBuffer processedBuf   = new ImageBuffer();
        ImageBuffer perspectiveBuf = new ImageBuffer();

        long prevFrameNs = 0;

        try {
            while (running) {
                if (pendingCameraChange) {
                    pendingCameraChange = false;
                    capture.release();
                    capture = new VideoCapture(inputSource.cameraIndex, Videoio.CAP_DSHOW);
                }

                long t0 = System.nanoTime();

                // --- acquire frame ---
                Mat loaded = inputSource.cloneAndClearLoadedMat();
                if (loaded != null) {
                    loaded.copyTo(frame.raw);
                    loaded.release();
                    if (lastRawSnapshot == null) lastRawSnapshot = new Mat();
                    frame.raw.copyTo(lastRawSnapshot);
                } else if (inputSource.frozen) {
                    if (frame.raw.empty()) { Thread.sleep(20); continue; }
                } else {
                    capture.read(frame.raw);
                    if (frame.raw.empty()) { Thread.sleep(5); continue; }
                    if (lastRawSnapshot == null) lastRawSnapshot = new Mat();
                    frame.raw.copyTo(lastRawSnapshot);
                }
                long t1 = System.nanoTime();

                WritableImage rawImg = rawBuf.update(frame.raw);
                long t2 = System.nanoTime();

                processor.process(frame, context);
                long t3 = System.nanoTime();

                WritableImage processedImg = processedBuf.update(frame.processed);
                long t4 = System.nanoTime();

                frame.raw.copyTo(perspectiveFrame.raw);
                perspectiveProcessor.process(perspectiveFrame, perspectiveContext);
                long t5 = System.nanoTime();

                WritableImage perspectiveImg = perspectiveFrame.processed.empty()
                        ? null : perspectiveBuf.update(perspectiveFrame.processed);
                long t6 = System.nanoTime();

                PreProcessingResult preProc = perspectiveFrame.processed.empty()
                        ? null : preProcessingProcessor.process(perspectiveFrame.processed);
                long t7 = System.nanoTime();

                TextLineExtractionResult linesResult = perspectiveFrame.processed.empty()
                        ? null : lineExtractor.process(perspectiveFrame.processed);
                long t8 = System.nanoTime();

                double fps = prevFrameNs > 0 ? 1e9 / (t0 - prevFrameNs) : 0;
                prevFrameNs = t0;

                FrameStats stats = new FrameStats(
                        frame.raw.cols(), frame.raw.rows(), fps,
                        ns2ms(t1 - t0), ns2ms(t2 - t1),
                        ns2ms(t3 - t2), ns2ms(t4 - t3),
                        ns2ms(t5 - t4), ns2ms(t6 - t5),
                        ns2ms(t8 - t0)
                );

                Platform.runLater(() -> {
                    rawImageProperty.set(rawImg);
                    processedImageProperty.set(processedImg);
                    if (perspectiveImg != null) perspectiveImageProperty.set(perspectiveImg);
                    if (preProc        != null) preProcessingProperty.set(preProc);
                    if (linesResult    != null) textLinesProperty.set(linesResult);
                    frameStatsProperty.set(stats);
                });

                if (inputSource.frozen) Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {
        } finally {
            capture.release();
            frame.release();
            perspectiveFrame.release();
            context.release();
            perspectiveContext.release();
            rawBuf.release();
            processedBuf.release();
            perspectiveBuf.release();
            preProcessingProcessor.release();
            lineExtractor.release();
            inputSource.release();
            if (lastRawSnapshot != null) lastRawSnapshot.release();
        }
    }

    private static long ns2ms(long ns) { return ns / 1_000_000; }
}
