package fr.an.textreco.model;

import java.util.concurrent.atomic.AtomicReference;

import static fr.an.textreco.model.GridDetectorMode.CORRELATION;

/**
 * Shared pipeline state. Written by the @Scheduled pipeline thread,
 * read by REST controllers. AtomicReference for object results, volatile
 * for scalar values — no synchronization needed for readers.
 */
public class ProcessingContext {

    // pipeline outputs
    private final AtomicReference<PreProcessingResult> preProcessing = new AtomicReference<>();
    private final AtomicReference<GridDetectionResult> gridDetection = new AtomicReference<>();
    private final AtomicReference<CorrelationGridDetectionResult> correlationGridDetection = new AtomicReference<>();
    private final AtomicReference<TextLineExtractionResult> textLines = new AtomicReference<>();
    private final AtomicReference<FrameStats> frameStats = new AtomicReference<>();
    private volatile String ocrText = "";
    private volatile String tessOcrText = "";
    private volatile boolean ocrEnabled = false;

    // shared mutable inputs
    public final InputSource inputSource = new InputSource();
    public final AppSettings appSettings = new AppSettings();
    public final EdgeDetectorSettings edgeDetectorSettings = new EdgeDetectorSettings();
    public final PreProcessingSettings preProcessingSettings = new PreProcessingSettings();
    public final GridDetectorSettings gridDetectorSettings = new GridDetectorSettings();
    public volatile GridDetectorMode gridDetectorMode = CORRELATION;
    public final CorrelationGridDetectorSettings correlationGridDetectorSettings = new CorrelationGridDetectorSettings();
    public final GridDetectCoordModel gridForcedValues = new GridDetectCoordModel();

    // one-shot OCR request
    private volatile boolean runTessOcrOnce = false;

    public void requestOcrOnce() {
        runTessOcrOnce = true;
    }

    public boolean consumeOcrOnce() {
        if (!runTessOcrOnce) return false;
        runTessOcrOnce = false;
        return true;
    }

    // getters for outputs
    public PreProcessingResult getPreProcessing() { return preProcessing.get(); }
    public void setPreProcessing(PreProcessingResult v) { preProcessing.set(v); }

    public GridDetectionResult getGridDetection() { return gridDetection.get(); }
    public void setGridDetection(GridDetectionResult v) { gridDetection.set(v); }

    public CorrelationGridDetectionResult getCorrelationGridDetection() { return correlationGridDetection.get(); }
    public void setCorrelationGridDetection(CorrelationGridDetectionResult v) { correlationGridDetection.set(v); }

    public TextLineExtractionResult getTextLines() { return textLines.get(); }
    public void setTextLines(TextLineExtractionResult v) { textLines.set(v); }

    public FrameStats getFrameStats() { return frameStats.get(); }
    public void setFrameStats(FrameStats v) { frameStats.set(v); }

    public String getOcrText() { return ocrText; }
    public void setOcrText(String v) { ocrText = v; }

    public String getTessOcrText() { return tessOcrText; }
    public void setTessOcrText(String v) { tessOcrText = v; }

    public boolean isOcrEnabled() { return ocrEnabled; }
    public void setOcrEnabled(boolean v) { ocrEnabled = v; }
}
