package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import javafx.beans.property.ObjectProperty;

public class PreProcessingViewModel implements ViewModel {

    private final ProcessingContext context;

    public PreProcessingViewModel(ProcessingContext context) {
        this.context = context;
    }

    public ObjectProperty<PreProcessingResult> preProcessingProperty() {
        return context.preProcessingProperty;
    }
}
