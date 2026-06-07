package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import javafx.beans.property.ObjectProperty;
import javafx.scene.image.WritableImage;

public class PerspectiveTransformViewModel implements ViewModel {

    private final ProcessingContext context;
    private final PerspectiveTransformProcessor processor;

    public PerspectiveTransformViewModel(ProcessingContext context,
                                         PerspectiveTransformProcessor processor) {
        this.context = context;
        this.processor = processor;
    }

    public ObjectProperty<WritableImage> rawImageProperty() {
        return context.rawImageProperty;
    }

    public ObjectProperty<WritableImage> perspectiveImageProperty() {
        return context.perspectiveImageProperty;
    }

    public PerspectiveTransformProcessor getProcessor() {
        return processor;
    }
}
