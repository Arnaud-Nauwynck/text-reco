package fr.an.textreco.ui;

import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.InputSource;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.CameraCapture;
import fr.an.textreco.processing.CharTemplateClassifier;
import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.processing.GridDetectorProcessor;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TessOcrProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.util.FxImageUtils.ImageBuffer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.WritableImage;
import lombok.Getter;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.File;
import java.util.Objects;

/**
 * Controller of the MVC pattern: coordinates the processing pipeline on a
 * background thread and publishes results into the {@link ProcessingContext}
 * (Model) observable properties for the View to consume.
 *
 * <p>Owns no OpenCV Mats or ImageBuffers directly — those live in the
 * processors.  Observable state lives in {@link ProcessingContext}.
 */
public class ProcessingPipeline {

    // -------------------------------------------------------------------------
    // model (observable outputs — FX thread)
    // -------------------------------------------------------------------------

    @Getter private final ProcessingContext context;

    // -------------------------------------------------------------------------
    // processors (controller-internal)
    // -------------------------------------------------------------------------

    @Getter private final CameraCapture             cameraCapture;
    private final EdgeDetectorProcessor             edgeDetector;
    private final PerspectiveTransformProcessor     perspectiveProcessor;
    private final PreProcessingProcessor            preProcessingProcessor;
    private final TextLineExtractorProcessor        lineExtractor;
    @Getter private final GridDetectorProcessor     gridDetector;
    @Getter private final CharTemplateClassifier    charClassifier = new CharTemplateClassifier();
    private final TessOcrProcessor                  tessOcr       = new TessOcrProcessor();

    // ImageBuffers for raw and perspective — conversions that don't belong to a single processor
    private final ImageBuffer rawImageBuf         = new ImageBuffer();
    private final ImageBuffer edgeImageBuf        = new ImageBuffer();
    private final ImageBuffer perspectiveImageBuf = new ImageBuffer();

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    private volatile boolean running;
    private Thread worker;

    public ProcessingPipeline(ProcessingContext context,
                              EdgeDetectorProcessor edgeDetector,
                              PerspectiveTransformProcessor perspectiveProcessor,
                              PreProcessingProcessor preProcessingProcessor,
                              TextLineExtractorProcessor lineExtractor) {
        this.context               = context;
        this.edgeDetector          = edgeDetector;
        this.perspectiveProcessor  = perspectiveProcessor;
        this.preProcessingProcessor = preProcessingProcessor;
        this.lineExtractor         = lineExtractor;
        this.gridDetector          = new GridDetectorProcessor(context.gridDetectorSettings);
        this.cameraCapture         = new CameraCapture(context.inputSource);
    }

    // -------------------------------------------------------------------------
    // property accessors — delegate to model (kept for backward-compat with Views)
    // -------------------------------------------------------------------------

    public ObjectProperty<WritableImage>            getRawImageProperty()         { return context.rawImageProperty; }
    public ObjectProperty<WritableImage>            getEdgeImageProperty()        { return context.edgeImageProperty; }
    public ObjectProperty<WritableImage>            getPerspectiveImageProperty() { return context.perspectiveImageProperty; }
    public ObjectProperty<PreProcessingResult>      getPreProcessingProperty()    { return context.preProcessingProperty; }
    public ObjectProperty<TextLineExtractionResult> getTextLinesProperty()        { return context.textLinesProperty; }
    public ObjectProperty<GridDetectionResult>      getGridDetectionProperty()    { return context.gridDetectionProperty; }
    public ObjectProperty<FrameStats>               getFrameStatsProperty()       { return context.frameStatsProperty; }
    public StringProperty                           getOcrProperty()              { return context.ocrProperty; }
    public StringProperty                           getTessOcrProperty()          { return context.tessOcrProperty; }
    public BooleanProperty                          getOcrEnabledProperty()       { return context.ocrEnabledProperty; }

    public InputSource    getInputSource()  { return context.inputSource; }
    public BooleanProperty getFrozenProperty() { return context.inputSource.frozenProperty(); }

    /** Delegates to model — called from the FX thread (e.g. "Run once" button). */
    public void requestOcrOnce() { context.requestOcrOnce(); }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

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
        InputSource src = context.inputSource;
        src.setFrozen(!src.isFrozen());
    }

    public void loadImageFile(File file) {
        Mat mat = Imgcodecs.imread(file.getAbsolutePath());
        if (mat.empty()) { System.err.println("Could not load image: " + file); return; }
        context.inputSource.setLoadedMat(mat);
        context.inputSource.setFrozen(true);
    }

    public void saveRawImage(File file) {
        cameraCapture.saveSnapshot(file);
    }

    public void selectCamera(int index) {
        cameraCapture.selectCamera(index);
        context.inputSource.setFrozen(false);
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

                String ocrText = "";
                if (linesResult != null && gridResult != null && !linesResult.lines().isEmpty()) {
                    ocrText = classifyAllChars(linesResult, gridResult, warped);
                }

                boolean doTessOcr = !warped.empty()
                        && (context.ocrEnabledProperty.get() || context.consumeOcrOnce());
                long tessOcrStart = System.nanoTime();
                String tessOcrText = null;
                if (doTessOcr) {
                    tessOcrText = tessOcr.recognize(warped);
                }
                long tessOcrMs = doTessOcr ? (System.nanoTime() - tessOcrStart) / 1_000_000 : -1;

                double fps = prevFrameNs > 0 ? 1e9 / (t0 - prevFrameNs) : 0;
                prevFrameNs = t0;

                FrameStats stats = new FrameStats(
                        raw.cols(), raw.rows(), fps,
                        ns2ms(t1 - t0), ns2ms(t2 - t1),
                        ns2ms(t3 - t2), ns2ms(t4 - t3),
                        ns2ms(t5 - t4), ns2ms(t6 - t5),
                        ns2ms(t8 - t0), tessOcrMs);

                final WritableImage fPerspImg    = perspectiveImg;
                final PreProcessingResult fPreProc = preProc;
                final TextLineExtractionResult fLines = linesResult;
                final GridDetectionResult fGrid  = gridResult;
                final String fOcrText            = ocrText;
                final String fTessOcrText        = tessOcrText;
                Platform.runLater(() -> {
                    context.rawImageProperty        .set(rawImg);
                    context.edgeImageProperty       .set(edgeImg);
                    if (fPerspImg   != null) context.perspectiveImageProperty.set(fPerspImg);
                    if (fPreProc    != null) context.preProcessingProperty   .set(fPreProc);
                    if (fLines      != null) context.textLinesProperty       .set(fLines);
                    if (fGrid       != null) context.gridDetectionProperty   .set(fGrid);
                    if (fOcrText    != null
                            && !Objects.equals(context.ocrProperty.get(), fOcrText)) {
                        context.ocrProperty.set(fOcrText);
                    }
                    if (fTessOcrText != null) context.tessOcrProperty        .set(fTessOcrText);
                    context.frameStatsProperty.set(stats);
                });

                if (context.inputSource.isFrozen()) Thread.sleep(50);
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
            context.inputSource.release();
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
        int charWInt  = (int) Math.max(1, Math.round(charW));
        int x0Int     = (int) Math.round(charX0);
        int fwdCount  = fw > 0 && charWInt > 0 ? (fw - x0Int + charWInt - 1) / charWInt : 0;
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
