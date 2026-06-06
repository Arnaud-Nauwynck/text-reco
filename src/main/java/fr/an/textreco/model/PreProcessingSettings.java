package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Observable settings for the pre-processing (binarisation + morphology) stage.
 * Kept in the model layer so views can bind directly without depending on the processor.
 */
public class PreProcessingSettings implements ViewModel {

    public final ObjectProperty<BinarizationMethod> binarizationMethod =
            new SimpleObjectProperty<>(BinarizationMethod.TOPHAT);

    public final IntegerProperty tophatRadius    = new SimpleIntegerProperty(12);
    public final IntegerProperty tophatThreshold = new SimpleIntegerProperty(20);
    public final IntegerProperty adaptiveBlock   = new SimpleIntegerProperty(31);
    public final IntegerProperty adaptiveC       = new SimpleIntegerProperty(10);
    public final IntegerProperty seHalfLen       = new SimpleIntegerProperty(7);
}
