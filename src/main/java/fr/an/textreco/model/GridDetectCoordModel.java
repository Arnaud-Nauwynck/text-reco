package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;

/**
 * Shared model holding the four grid-coordinate "forced value" control groups
 * (line height, char width, line offset, char offset).
 *
 * <p>Each is a {@link ForcedValueModel} carrying its own label/unit plus the
 * observable {@code computedValue} / {@code force} / {@code forcedValue} triplet.
 * One instance lives in {@link ProcessingContext} so the same models are bound
 * by every tab that displays these controls (Char Classifier, Grid Detect,
 * Line Height, Char Width, Line Offset), keeping their force flags and values
 * in sync across tabs.
 */
public class GridDetectCoordModel implements ViewModel {

    public final ForcedValueModel lineHeight = new ForcedValueModel("LineH:", "px", 28.0);
    public final ForcedValueModel charWidth = new ForcedValueModel("CharW:", "px", 14.0);
    public final ForcedValueModel lineOffset = new ForcedValueModel("y0", "px", 0.0);
    public final ForcedValueModel charOffset = new ForcedValueModel("x0", "px", 0.0);
}
