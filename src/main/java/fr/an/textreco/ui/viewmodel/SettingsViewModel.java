package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.model.GridDetectorSettings;
import fr.an.textreco.model.PreProcessingSettings;
import fr.an.textreco.model.ProcessingContext;

public class SettingsViewModel implements ViewModel {

    public final AppSettings           appSettings;
    public final EdgeDetectorSettings  edgeDetectorSettings;
    public final PreProcessingSettings preProcessingSettings;
    public final GridDetectorSettings  gridDetectorSettings;

    public SettingsViewModel(ProcessingContext context) {
        this.appSettings           = context.appSettings;
        this.edgeDetectorSettings  = context.edgeDetectorSettings;
        this.preProcessingSettings = context.preProcessingSettings;
        this.gridDetectorSettings  = context.gridDetectorSettings;
    }
}
