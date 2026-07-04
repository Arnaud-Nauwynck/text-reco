package fr.an.textreco.service;

import fr.an.textreco.model.*;
import fr.an.textreco.processing.*;
import fr.an.textreco.util.MatFacade;
import fr.an.textreco.util.MatTracker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Spring equivalent of the old ProcessingPipeline: orchestrates the OpenCV
 * pipeline on a @Scheduled background thread and stores results in ProcessingContext.
 * Mat snapshots for display are stored in AtomicReferences and encoded on-demand
 * by MatEncoderService when a REST request arrives.
 */
@Service
public class ProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ProcessingService.class);

    @Getter
    private final ProcessingContext context = new ProcessingContext();

    // processors
    private final EdgeDetectorProcessor edgeDetector;
    private final PerspectiveTransformProcessor perspectiveProcessor;
    private final PreProcessingProcessor preProcessingProcessor;
    private final TextLineExtractorProcessor lineExtractor;
    @Getter
    private final GridDetectorProcessor gridDetector;
    @Getter
    private final CorrelationGridDetectorProcessor correlationGridDetector;
    @Getter
    private final CharTemplateDb charTemplateDb;
    @Getter
    private final CharTemplateClassifier charClassifier;
    private final TessOcrProcessor tessOcr;
    @Getter
    private final CameraCapture cameraCapture;

    // display Mat snapshots — pipeline writes clones, REST reads them
    private final AtomicReference<Mat> latestRaw = new AtomicReference<>(new Mat());
    private final AtomicReference<Mat> latestEdge = new AtomicReference<>(new Mat());
    private final AtomicReference<Mat> latestPerspective = new AtomicReference<>(new Mat());

    // cached available cameras (probed once at startup)
    @Getter
    private volatile List<CameraDevice> availableCameras = List.of();

    public ProcessingService() {
        edgeDetector = new EdgeDetectorProcessor(context.edgeDetectorSettings);
        perspectiveProcessor = new PerspectiveTransformProcessor();
        preProcessingProcessor = new PreProcessingProcessor(
                context.preProcessingSettings, context.appSettings);
        lineExtractor = new TextLineExtractorProcessor();
        gridDetector = new GridDetectorProcessor(context.gridDetectorSettings);
        correlationGridDetector = new CorrelationGridDetectorProcessor(
                context.correlationGridDetectorSettings);
        charTemplateDb = new CharTemplateDb();
        charClassifier = new CharTemplateClassifier(charTemplateDb);
        tessOcr = new TessOcrProcessor();
        cameraCapture = new CameraCapture(context.inputSource);
    }

    @PostConstruct
    public void init() {
        nu.pattern.OpenCV.loadLocally();
        cameraCapture.open();
        MatFacade.beginRunLoop();

        // probe cameras in background to not block startup
        Thread.ofVirtual().start(() -> {
            List<CameraDevice> cams = CameraCapture.probeAvailableCameras(4, 3000);
            availableCameras = cams;
            log.info("Found {} camera(s)", cams.size());
        });
    }

    @PreDestroy
    public void destroy() {
        MatFacade.beginInit();
        cameraCapture.release();
        edgeDetector.release();
        perspectiveProcessor.release();
        preProcessingProcessor.release();
        lineExtractor.release();
        gridDetector.release();
        correlationGridDetector.release();
        tessOcr.release();
        charClassifier.release();
        charTemplateDb.release();
        context.inputSource.release();
    }

    // -------------------------------------------------------------------------
    // pipeline loop
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${textreco.pipeline.interval-ms:500}")
    public void runPipelineIteration() {
        try {
            runOnce();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Pipeline iteration error", e);
        }
    }

    private void runOnce() throws InterruptedException {
        long t0 = System.nanoTime();

        if (!cameraCapture.readFrame()) {
            return;
        }
        Mat raw = cameraCapture.getRaw();
        long t1 = System.nanoTime();

        edgeDetector.process(raw);
        long t2 = System.nanoTime();

        perspectiveProcessor.process(raw);
        long t3 = System.nanoTime();

        Mat warped = perspectiveProcessor.getWarped();

        PreProcessingResult preProc = warped.empty()
                ? null : preProcessingProcessor.process(warped);
        long t4 = System.nanoTime();

        GridDetectionResult gridResult = null;
        CorrelationGridDetectionResult corrResult = null;
        if (preProc != null) {
            if (context.gridDetectorMode == GridDetectorMode.CORRELATION) {
                corrResult = correlationGridDetector.process(warped);
                gridResult = corrResult.toGridDetectionResult();
            } else {
                gridResult = gridDetector.processFromSums(
                        preProc.hRowSums(), warped.rows(),
                        preProc.vColSums(), warped.cols());
            }
        }

        TextLineExtractionResult linesResult = (preProc == null)
                ? null : lineExtractor.process(preProc.hRowSums(),
                preProcessingProcessor.binary, gridResult);
        long t5 = System.nanoTime();

        String ocrText = "";
        if (linesResult != null && gridResult != null && !linesResult.lines().isEmpty()) {
            ocrText = classifyAllChars(linesResult, gridResult, preProcessingProcessor.binary);
        }

        boolean doTessOcr = !warped.empty()
                && (context.isOcrEnabled() || context.consumeOcrOnce());
        long tessOcrStart = System.nanoTime();
        String tessOcrText = null;
        if (doTessOcr) {
            tessOcrText = tessOcr.recognize(warped);
        }
        long tessOcrMs = doTessOcr ? (System.nanoTime() - tessOcrStart) / 1_000_000 : -1;

        long t6 = System.nanoTime();

        // snapshot Mats for REST access — clone into AtomicReference holders
        latestRaw.set(raw.clone());
        latestEdge.set(edgeDetector.getProcessed().clone());
        if (!warped.empty()) {
            latestPerspective.set(warped.clone());
        }

        double fps = 0; // not tracked across calls anymore
        FrameStats stats = new FrameStats(
                raw.cols(), raw.rows(), fps,
                ns2ms(t1 - t0), 0,
                ns2ms(t2 - t1), 0,
                ns2ms(t3 - t2), 0,
                ns2ms(t6 - t0), tessOcrMs);

        // publish results to context
        if (preProc != null) context.setPreProcessing(preProc);
        if (linesResult != null) context.setTextLines(linesResult);
        if (gridResult != null) context.setGridDetection(gridResult);
        if (corrResult != null) context.setCorrelationGridDetection(corrResult);
        if (!Objects.equals(context.getOcrText(), ocrText)) {
            context.setOcrText(ocrText);
        }
        if (tessOcrText != null) context.setTessOcrText(tessOcrText);
        context.setFrameStats(stats);

        MatTracker.logIfDue();
        MatFacade.frameTick();

        if (context.inputSource.isFrozen()) Thread.sleep(50);
    }

    // -------------------------------------------------------------------------
    // Mat accessors for REST controllers
    // -------------------------------------------------------------------------

    public Mat getLatestRaw() { return latestRaw.get(); }
    public Mat getLatestEdge() { return latestEdge.get(); }
    public Mat getLatestPerspective() { return latestPerspective.get(); }
    public Mat getLatestBinary() { return preProcessingProcessor.binary; }
    public PreProcessingProcessor getPreProcessingProcessor() { return preProcessingProcessor; }
    public TextLineExtractorProcessor getLineExtractor() { return lineExtractor; }

    public Mat getMorphMat(String type) {
        return switch (type) {
            case "morph-horiz"    -> preProcessingProcessor.morphHorizMat;
            case "morph-vert"     -> preProcessingProcessor.morphVertMat;
            case "morph-diag-fwd" -> preProcessingProcessor.morphDiagFwdMat;
            case "morph-diag-bwd" -> preProcessingProcessor.morphDiagBwdMat;
            case "close-horiz"    -> preProcessingProcessor.closeHorizMat;
            case "close-vert"     -> preProcessingProcessor.closeVertMat;
            case "close-diag-fwd" -> preProcessingProcessor.closeDiagFwdMat;
            case "close-diag-bwd" -> preProcessingProcessor.closeDiagBwdMat;
            default               -> null;
        };
    }

    // -------------------------------------------------------------------------
    // char classification (same logic as ProcessingPipeline.classifyAllChars)
    // -------------------------------------------------------------------------

    private String classifyAllChars(TextLineExtractionResult lines,
                                    GridDetectionResult grid,
                                    Mat binary) {
        double charW = grid.bestCharW();
        double charX0 = grid.bestCharX0();
        double lineH = grid.bestLineH();
        int fw = binary.cols();
        if (charW <= 0 || lineH <= 0) return "";

        int charWInt = (int) Math.max(1, Math.round(charW));
        int x0Int = (int) Math.round(charX0);
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
            int lineBot = Math.min(line.rowEnd(), binary.rows());
            int lh = lineBot - lineTop;
            if (lh <= 0) { sb.append('\n'); continue; }
            for (int ci = 0; ci < colStarts.length; ci++) {
                int cx = Math.max(0, colStarts[ci]);
                int cEnd = ci + 1 < colStarts.length ? colStarts[ci + 1] : cx + charWInt;
                int cw = Math.max(1, Math.min(cEnd - cx, fw - cx));
                if (cx >= fw) break;
                Mat crop = binary.submat(lineTop, lineBot, cx, cx + cw);
                CharTemplateClassifier.Result r = charClassifier.classify(crop);
                crop.release();
                sb.append(r.isConfident() ? r.ch() : '?');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static long ns2ms(long ns) {
        return ns / 1_000_000;
    }
}
