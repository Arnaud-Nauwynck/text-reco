package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.CharTemplateClassifier;
import javafx.beans.property.ObjectProperty;

public class CharClassifierViewModel implements ViewModel {

    private final ProcessingContext       context;
    private final CharTemplateClassifier  charClassifier;

    public CharClassifierViewModel(ProcessingContext context, CharTemplateClassifier charClassifier) {
        this.context        = context;
        this.charClassifier = charClassifier;
    }

    public ObjectProperty<TextLineExtractionResult> textLinesProperty()    { return context.textLinesProperty; }
    public ObjectProperty<GridDetectionResult>      gridDetectionProperty() { return context.gridDetectionProperty; }
    public ObjectProperty<PreProcessingResult>      preProcessingProperty() { return context.preProcessingProperty; }

    public CharTemplateClassifier getCharClassifier() { return charClassifier; }
}
