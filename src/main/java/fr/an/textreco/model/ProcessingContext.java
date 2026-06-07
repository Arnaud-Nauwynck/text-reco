package fr.an.textreco.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.WritableImage;

import static fr.an.textreco.model.GridDetectorMode.CORRELATION;

/**
 * Model of the MVC pattern: holds all observable pipeline outputs that the
 * View layers observe, plus the shared {@link InputSource} and the
 * run-OCR-once flag that the View can set.
 *
 * <p>This class has no dependency on OpenCV, processors, or JavaFX controls —
 * only on JavaFX properties and model types, keeping it strictly in the model layer.
 *
 * <p>All {@code ObjectProperty} / {@code StringProperty} / {@code BooleanProperty}
 * fields must be read and written on the JavaFX Application Thread, except where
 * noted (e.g. {@link #requestOcrOnce()} which uses a volatile flag).
 */
public class ProcessingContext {

    // -------------------------------------------------------------------------
    // observable outputs (FX thread)
    // -------------------------------------------------------------------------

    public final ObjectProperty<WritableImage> rawImageProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<WritableImage> edgeImageProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<WritableImage> perspectiveImageProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<PreProcessingResult> preProcessingProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<TextLineExtractionResult> textLinesProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<GridDetectionResult> gridDetectionProperty = new SimpleObjectProperty<>();
    public final ObjectProperty<FrameStats> frameStatsProperty = new SimpleObjectProperty<>();
    public final StringProperty ocrProperty = new SimpleStringProperty();
    public final StringProperty tessOcrProperty = new SimpleStringProperty();
    public final BooleanProperty ocrEnabledProperty = new SimpleBooleanProperty(false);

    // -------------------------------------------------------------------------
    // shared input-source state
    // -------------------------------------------------------------------------

    /**
     * Mutable input-source state shared between the FX thread and the camera loop.
     */
    public final InputSource inputSource = new InputSource();

    // -------------------------------------------------------------------------
    // settings (observable, bound by views and read by processors)
    // -------------------------------------------------------------------------

    public final AppSettings appSettings = new AppSettings();
    public final EdgeDetectorSettings edgeDetectorSettings = new EdgeDetectorSettings();
    public final PreProcessingSettings preProcessingSettings = new PreProcessingSettings();
    public final GridDetectorSettings gridDetectorSettings = new GridDetectorSettings();

    public final ObjectProperty<GridDetectorMode> gridDetectorMode = new SimpleObjectProperty<>(CORRELATION);
    public final CorrelationGridDetectorSettings correlationGridDetectorSettings = new CorrelationGridDetectorSettings();
    public final ObjectProperty<CorrelationGridDetectionResult> correlationGridDetectionProperty = new SimpleObjectProperty<>();

    // -------------------------------------------------------------------------
    // one-shot OCR request (written from FX thread, read by camera thread)
    // -------------------------------------------------------------------------

    private volatile boolean runTessOcrOnce = false;

    /**
     * Called from the FX thread (e.g. "Run once" button). Thread-safe via volatile.
     */
    public void requestOcrOnce() {
        runTessOcrOnce = true;
    }

    /**
     * Called by the processing loop to consume the one-shot flag.
     * Returns {@code true} and clears the flag atomically.
     */
    public boolean consumeOcrOnce() {
        if (!runTessOcrOnce) return false;
        runTessOcrOnce = false;
        return true;
    }
}
