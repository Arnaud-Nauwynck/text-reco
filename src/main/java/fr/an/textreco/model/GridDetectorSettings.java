package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Model settings for the grid detector (line-height and char-width detection).
 *
 * <p>All properties are JavaFX observable so views can bind to them directly.
 * The processor reads them via {@code .get()} on every frame — JavaFX property
 * reads are thread-safe (volatile under the hood).
 */
public class GridDetectorSettings implements ViewModel {

    // -------------------------------------------------------------------------
    // Search range (pixels)
    // -------------------------------------------------------------------------

    public final IntegerProperty minLineH = new SimpleIntegerProperty(25);
    public final IntegerProperty maxLineH = new SimpleIntegerProperty(80);
    public final IntegerProperty minCharW = new SimpleIntegerProperty(8);
    public final IntegerProperty maxCharW = new SimpleIntegerProperty(60);

    // -------------------------------------------------------------------------
    // Force line-height
    // -------------------------------------------------------------------------

    public final BooleanProperty forceLineH   = new SimpleBooleanProperty(false);
    public final DoubleProperty  forcedLineH  = new SimpleDoubleProperty(28.0);

    // -------------------------------------------------------------------------
    // Force char-width  (px value + convenience lineH/charW ratio)
    // -------------------------------------------------------------------------

    /** When true, skip X-axis valley detection and use {@link #forcedCharWPx} directly. */
    public final BooleanProperty forceCharWidth   = new SimpleBooleanProperty(false);
    /** Ratio lineH/charW (e.g. 2.0 → charW = lineH/2). Kept in sync with px value by the view. */
    public final DoubleProperty  forcedCharWRatio = new SimpleDoubleProperty(2.0);
    /** Direct forced char width in pixels. Used when {@link #forceCharWidth} is true. */
    public final DoubleProperty  forcedCharWPx    = new SimpleDoubleProperty(15.0);

    // -------------------------------------------------------------------------
    // Force grid offsets
    // -------------------------------------------------------------------------

    public final BooleanProperty forceLineY0  = new SimpleBooleanProperty(false);
    public final DoubleProperty  forcedLineY0 = new SimpleDoubleProperty(0.0);

    public final BooleanProperty forceCharX0  = new SimpleBooleanProperty(false);
    public final DoubleProperty  forcedCharX0 = new SimpleDoubleProperty(0.0);

    // -------------------------------------------------------------------------
    // Force number of lines / columns
    // -------------------------------------------------------------------------

    /** When true, override the auto-detected line count with {@link #forcedLineCount}. */
    public final BooleanProperty forceLineCount  = new SimpleBooleanProperty(false);
    public final IntegerProperty forcedLineCount = new SimpleIntegerProperty(24);

    /** When true, override the auto-detected column count with {@link #forcedColCount}. */
    public final BooleanProperty forceColCount   = new SimpleBooleanProperty(false);
    public final IntegerProperty forcedColCount  = new SimpleIntegerProperty(80);
}
