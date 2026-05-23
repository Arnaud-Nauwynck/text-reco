package fr.an.textreco.ui;

import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.InputSource;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.CameraCapture;
import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.processing.GridDetectorProcessor;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.CharTemplateClassifier;
import fr.an.textreco.processing.TessOcrProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.util.FxImageUtils.ImageBuffer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.WritableImage;
import lombok.Getter;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;

/**
 * Coordinates the processing pipeline on a background thread and publishes
 * results to JavaFX observable properties for the View to consume.
 *
 * Owns no OpenCV Mats or ImageBuffers directly — those live in the processors.
 */
public class ProcessingPipeline {

    // -------------------------------------------------------------------------
    // observable outputs (FX thread)
    // -------------------------------------------------------------------------

    @Getter private final ObjectProperty<WritableImage>            rawImageProperty         = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<WritableImage>            edgeImageProperty        = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<WritableImage>            perspectiveImageProperty = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<PreProcessingResult>      preProcessingProperty    = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<TextLineExtractionResult> textLinesProperty        = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<GridDetectionResult>      gridDetectionProperty    = new SimpleObjectProperty<>();
    @Getter private final ObjectProperty<FrameStats>               frameStatsProperty       = new SimpleObjectProperty<>();
    @Getter private final StringProperty                           tessOcrProperty          = new SimpleStringProperty();
    @Getter private final BooleanProperty                          ocrEnabledProperty       = new SimpleBooleanProperty(false);

    // -------------------------------------------------------------------------
    // processors
    // -------------------------------------------------------------------------

    @Getter private final InputSource                  inputSource;
    @Getter private final CameraCapture                cameraCapture;

    public BooleanProperty getFrozenProperty() { return inputSource.frozenProperty(); }
    private final EdgeDetectorProcessor        edgeDetector;
    private final PerspectiveTransformProcessor perspectiveProcessor;
    private final PreProcessingProcessor        preProcessingProcessor;
    private final TextLineExtractorProcessor    lineExtractor;
    @Getter private final GridDetectorProcessor    gridDetector = new GridDetectorProcessor();
    @Getter private final CharTemplateClassifier   charClassifier = new CharTemplateClassifier();
    private final TessOcrProcessor tessOcr = new TessOcrProcessor();

    private volatile boolean runOcrOnce = false;

    public void requestOcrOnce() { runOcrOnce = true; }

    // ImageBuffers for raw and perspective — conversions that don't belong to a single processor
    private final ImageBuffer rawImageBuf         = new ImageBuffer();
    private final ImageBuffer edgeImageBuf        = new ImageBuffer();
    private final ImageBuffer perspectiveImageBuf = new ImageBuffer();

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    private volatile boolean running;
    private Thread worker;

    public ProcessingPipeline(EdgeDetectorProcessor edgeDetector,
                               PerspectiveTransformProcessor perspectiveProcessor,
                               PreProcessingProcessor preProcessingProcessor,
                               TextLineExtractorProcessor lineExtractor) {
        this.edgeDetector           = edgeDetector;
        this.perspectiveProcessor   = perspectiveProcessor;
        this.preProcessingProcessor = preProcessingProcessor;
        this.lineExtractor          = lineExtractor;
        this.inputSource            = new InputSource();
        this.cameraCapture          = new CameraCapture(inputSource);
    }

    public void start() {
        cameraCapture.open();
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
        inputSource.setFrozen(!inputSource.isFrozen());
    }

    public void loadImageFile(File file) {
        Mat mat = Imgcodecs.imread(file.getAbsolutePath());
        if (mat.empty()) { System.err.println("Could not load image: " + file); return; }
        inputSource.setLoadedMat(mat);
        inputSource.setFrozen(true);
    }

    public void saveRawImage(File file) {
        cameraCapture.saveSnapshot(file);
    }

    public void selectCamera(int index) {
        cameraCapture.selectCamera(index);
        inputSource.setFrozen(false);
    }

    // -------------------------------------------------------------------------
    // pipeline loop (background thread)
    // -------------------------------------------------------------------------

    private void runLoop() {
        long prevFrameNs = 0;
        try {
            while (running) {
                long t0 = System.nanoTime();

                if (!cameraCapture.readFrame()) {
                    continue;
                }
                Mat raw = cameraCapture.getRaw();
                long t1 = System.nanoTime();

                WritableImage rawImg = rawImageBuf.update(raw);
                long t2 = System.nanoTime();

                edgeDetector.process(raw);
                long t3 = System.nanoTime();

                WritableImage edgeImg = edgeImageBuf.update(edgeDetector.getProcessed());
                long t4 = System.nanoTime();

                perspectiveProcessor.process(raw);
                long t5 = System.nanoTime();

                Mat warped = perspectiveProcessor.getWarped();
                WritableImage perspectiveImg = warped.empty()
                        ? null : perspectiveImageBuf.update(warped);
                long t6 = System.nanoTime();

                PreProcessingResult preProc = warped.empty()
                        ? null : preProcessingProcessor.process(warped);
                long t7 = System.nanoTime();

                GridDetectionResult gridResult = (preProc == null) ? null
                        : gridDetector.processFromSums(
                                preProc.hRowSums(), warped.rows(),
                                preProc.vColSums(), warped.cols());

                TextLineExtractionResult linesResult = (preProc == null)
                        ? null : lineExtractor.process(preProc.hRowSums(), warped, gridResult);
                long t8 = System.nanoTime();

                boolean doOcr = !warped.empty() && (ocrEnabledProperty.get() || runOcrOnce);
                if (runOcrOnce) runOcrOnce = false;
                long tOcrStart = System.nanoTime();
                String ocrText = null;
                if (doOcr) {
                    if (linesResult != null && gridResult != null && !linesResult.lines().isEmpty()) {
                        ocrText = classifyAllChars(linesResult, gridResult, warped);
                    } else {
                        ocrText = tessOcr.recognize(warped);
                    }
                }
                long tessOcrMs = doOcr ? (System.nanoTime() - tOcrStart) / 1_000_000 : -1;

                double fps = prevFrameNs > 0 ? 1e9 / (t0 - prevFrameNs) : 0;
                prevFrameNs = t0;

                FrameStats stats = new FrameStats(
                        raw.cols(), raw.rows(), fps,
                        ns2ms(t1 - t0), ns2ms(t2 - t1),
                        ns2ms(t3 - t2), ns2ms(t4 - t3),
                        ns2ms(t5 - t4), ns2ms(t6 - t5),
                        ns2ms(t8 - t0), tessOcrMs);

                final WritableImage fPerspImg = perspectiveImg;
                final PreProcessingResult fPreProc = preProc;
                final TextLineExtractionResult fLines = linesResult;
                final GridDetectionResult fGrid = gridResult;
                final String fOcrText = ocrText;
                Platform.runLater(() -> {
                    rawImageProperty        .set(rawImg);
                    edgeImageProperty       .set(edgeImg);
                    if (fPerspImg  != null) perspectiveImageProperty.set(fPerspImg);
                    if (fPreProc   != null) preProcessingProperty   .set(fPreProc);
                    if (fLines     != null) textLinesProperty        .set(fLines);
                    if (fGrid      != null) gridDetectionProperty   .set(fGrid);
                    if (fOcrText   != null) tessOcrProperty         .set(fOcrText);
                    frameStatsProperty.set(stats);
                });

                if (inputSource.isFrozen()) Thread.sleep(50);
            }
        } catch (InterruptedException ignored) {
        } finally {
            cameraCapture.release();
            edgeDetector.release();
            perspectiveProcessor.release();
            preProcessingProcessor.release();
            lineExtractor.release();
            gridDetector.release();
            tessOcr.release();
            charClassifier.release();
            rawImageBuf.release();
            edgeImageBuf.release();
            perspectiveImageBuf.release();
            inputSource.release();
        }
    }

    private String classifyAllChars(TextLineExtractionResult lines,
                                    GridDetectionResult grid,
                                    Mat warped) {
        double charW  = grid.bestCharW();
        double charX0 = grid.bestCharX0();
        double lineH  = grid.bestLineH();
        int    fw     = warped.cols();
        if (charW <= 0 || lineH <= 0) return "";

        // Rebuild column starts from the detected grid (same logic as ColumnsDetectionView)
        int charWInt = (int) Math.max(1, Math.round(charW));
        int x0Int    = (int) Math.round(charX0);
        int fwdCount = fw > 0 && charWInt > 0 ? (fw - x0Int + charWInt - 1) / charWInt : 0;
        int backCount = charWInt > 0 ? x0Int / charWInt : 0;
        int[] colStarts = new int[backCount + fwdCount];
        for (int i = 0; i < backCount; i++)
            colStarts[i] = (int) Math.round(charX0 - (backCount - i) * charW);
        for (int i = 0; i < fwdCount; i++)
            colStarts[backCount + i] = (int) Math.round(charX0 + i * charW);

        StringBuilder sb = new StringBuilder();
        for (var line : lines.lines()) {
            int lineTop = line.rowStart();
            int lineBot = Math.min(line.rowEnd(), warped.rows());
            int lh = lineBot - lineTop;
            if (lh <= 0) { sb.append('\n'); continue; }
            for (int ci = 0; ci < colStarts.length; ci++) {
                int cx   = Math.max(0, colStarts[ci]);
                int cEnd = ci + 1 < colStarts.length ? colStarts[ci + 1] : cx + charWInt;
                int cw   = Math.max(1, Math.min(cEnd - cx, fw - cx));
                if (cx >= fw) break;
                Mat crop = warped.submat(lineTop, lineBot, cx, cx + cw);
                CharTemplateClassifier.Result r = charClassifier.classify(crop);
                crop.release();
                sb.append(r.isConfident() ? r.ch() : '?');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static long ns2ms(long ns) { return ns / 1_000_000; }
}
