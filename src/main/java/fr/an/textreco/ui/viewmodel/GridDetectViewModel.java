package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.model.CorrelationGridDetectorSettings;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.GridDetectorMode;
import fr.an.textreco.model.GridDetectorSettings;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.model.TextLineExtractionResult;
import javafx.beans.property.ObjectProperty;

public class GridDetectViewModel implements ViewModel {

    private final ProcessingContext context;

    public GridDetectViewModel(ProcessingContext context) {
        this.context = context;
    }

    public ObjectProperty<PreProcessingResult> preProcessingProperty() {
        return context.preProcessingProperty;
    }

    public ObjectProperty<TextLineExtractionResult> textLinesProperty() {
        return context.textLinesProperty;
    }

    public ObjectProperty<GridDetectionResult> gridDetectionProperty() {
        return context.gridDetectionProperty;
    }

    public ObjectProperty<CorrelationGridDetectionResult> correlationGridDetectionProperty() {
        return context.correlationGridDetectionProperty;
    }

    public ObjectProperty<GridDetectorMode> gridDetectorMode() {
        return context.gridDetectorMode;
    }

    public GridDetectorSettings getGridDetectorSettings() {
        return context.gridDetectorSettings;
    }

    public CorrelationGridDetectorSettings getCorrelationGridDetectorSettings() {
        return context.correlationGridDetectorSettings;
    }
}
