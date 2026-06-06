package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Model settings for the correlation-based grid detector.
 */
public class CorrelationGridDetectorSettings implements ViewModel {

    public final IntegerProperty minLineHeight = new SimpleIntegerProperty(8);
    public final IntegerProperty maxLineHeight = new SimpleIntegerProperty(40);

    /** Exponential smoothing factor: 0 = frozen, 1 = instant. */
    public final DoubleProperty smoothingAlpha = new SimpleDoubleProperty(0.15);
}
