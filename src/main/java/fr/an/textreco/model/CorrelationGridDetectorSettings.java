package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Model settings for the correlation-based grid detector.
 */
public class CorrelationGridDetectorSettings implements ViewModel {

    public final IntegerProperty minLineHeight = new SimpleIntegerProperty(8);
    public final IntegerProperty maxLineHeight = new SimpleIntegerProperty(40);

    public final IntegerProperty minCharWidth = new SimpleIntegerProperty(4);
    public final IntegerProperty maxCharWidth = new SimpleIntegerProperty(30);

    // -------------------------------------------------------------------------
    // Force overrides — when enabled, the detector skips its estimate and uses
    // the forced value directly (offsets still feed the grid overlay/output).
    // -------------------------------------------------------------------------

    /**
     * When true, skip Y autocorrelation and use {@link #forcedLineHeight} directly.
     */
    public final BooleanProperty forceLineHeight = new SimpleBooleanProperty(false);
    public final DoubleProperty forcedLineHeight = new SimpleDoubleProperty(28.0);

    /**
     * When true, skip X autocorrelation and use {@link #forcedCharWidth} directly.
     */
    public final BooleanProperty forceCharWidth = new SimpleBooleanProperty(false);
    public final DoubleProperty forcedCharWidth = new SimpleDoubleProperty(14.0);

    /**
     * When true, skip Y phase search and use {@link #forcedLineOffset} directly.
     */
    public final BooleanProperty forceLineOffset = new SimpleBooleanProperty(false);
    public final DoubleProperty forcedLineOffset = new SimpleDoubleProperty(0.0);

    /**
     * When true, skip X phase search and use {@link #forcedColOffset} directly.
     */
    public final BooleanProperty forceColOffset = new SimpleBooleanProperty(false);
    public final DoubleProperty forcedColOffset = new SimpleDoubleProperty(0.0);

    /**
     * Exponential smoothing factor: 0 = frozen, 1 = instant.
     */
    public final DoubleProperty smoothingAlpha = new SimpleDoubleProperty(0.15);
}
