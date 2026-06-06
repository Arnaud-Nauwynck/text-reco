package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import de.saxsys.mvvmfx.utils.commands.Action;
import de.saxsys.mvvmfx.utils.commands.DelegateCommand;
import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.ProcessingContext;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.StringProperty;

public class TessOcrViewModel implements ViewModel {

    private final ProcessingContext context;

    public final DelegateCommand runOnceCommand;

    public TessOcrViewModel(ProcessingContext context) {
        this.context = context;

        runOnceCommand = new DelegateCommand(() -> new Action() {
            @Override protected void action() { context.requestOcrOnce(); }
        });
    }

    public BooleanProperty              ocrEnabledProperty()  { return context.ocrEnabledProperty; }
    public StringProperty               tessOcrProperty()     { return context.tessOcrProperty; }
    public ObjectProperty<FrameStats>   frameStatsProperty()  { return context.frameStatsProperty; }
}
