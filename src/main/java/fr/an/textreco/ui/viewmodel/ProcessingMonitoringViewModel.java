package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.ProcessingContext;
import javafx.beans.property.ObjectProperty;

public class ProcessingMonitoringViewModel implements ViewModel {

    private final ProcessingContext context;

    public ProcessingMonitoringViewModel(ProcessingContext context) {
        this.context = context;
    }

    public ObjectProperty<FrameStats> frameStatsProperty() {
        return context.frameStatsProperty;
    }
}
