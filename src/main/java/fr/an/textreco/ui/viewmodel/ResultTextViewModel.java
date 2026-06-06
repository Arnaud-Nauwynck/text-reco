package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.ProcessingContext;
import javafx.beans.property.StringProperty;

public class ResultTextViewModel implements ViewModel {

    private final ProcessingContext context;

    public ResultTextViewModel(ProcessingContext context) {
        this.context = context;
    }

    public StringProperty ocrProperty() { return context.ocrProperty; }
}
