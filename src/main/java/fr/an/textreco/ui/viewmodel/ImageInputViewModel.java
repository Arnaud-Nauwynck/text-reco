package fr.an.textreco.ui.viewmodel;

import de.saxsys.mvvmfx.ViewModel;
import de.saxsys.mvvmfx.utils.commands.Action;
import de.saxsys.mvvmfx.utils.commands.DelegateCommand;
import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.ui.ProcessingPipeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.WritableImage;

import java.io.File;
import java.util.List;

public class ImageInputViewModel implements ViewModel {

    public static final String NOTIFICATION_OPEN_FILE = "OPEN_FILE";
    public static final String NOTIFICATION_SAVE_FILE = "SAVE_FILE";

    private final ProcessingContext context;
    private final ProcessingPipeline pipeline;

    public final ObservableList<CameraDevice> cameras = FXCollections.observableArrayList();
    private final ObjectProperty<CameraDevice> selectedCamera = new SimpleObjectProperty<>();

    public final DelegateCommand toggleFreezeCommand;
    public final DelegateCommand openFileCommand;
    public final DelegateCommand saveFileCommand;

    public ImageInputViewModel(ProcessingContext context, ProcessingPipeline pipeline) {
        this.context = context;
        this.pipeline = pipeline;

        toggleFreezeCommand = new DelegateCommand(() -> new Action() {
            @Override
            protected void action() {
                pipeline.toggleFreeze();
            }
        });

        openFileCommand = new DelegateCommand(() -> new Action() {
            @Override
            protected void action() {
                publish(NOTIFICATION_OPEN_FILE);
            }
        });

        saveFileCommand = new DelegateCommand(() -> new Action() {
            @Override
            protected void action() {
                publish(NOTIFICATION_SAVE_FILE);
            }
        });
    }

    // ── Properties exposed to the View ────────────────────────────────────────

    public ObjectProperty<WritableImage> rawImageProperty() {
        return context.rawImageProperty;
    }

    public ObjectProperty<PreProcessingResult> preProcessingProperty() {
        return context.preProcessingProperty;
    }

    public ReadOnlyBooleanProperty frozenProperty() {
        return context.inputSource.frozenProperty();
    }

    public ObjectProperty<CameraDevice> selectedCameraProperty() {
        return selectedCamera;
    }

    // ── Actions invoked by the View ───────────────────────────────────────────

    public void selectCamera(CameraDevice device) {
        if (device != null && !context.inputSource.isFrozen()) {
            pipeline.selectCamera(device.index());
        }
    }

    public void loadFile(File file) {
        if (file != null) pipeline.loadImageFile(file);
    }

    public void saveRawImage(File file) {
        if (file != null) pipeline.saveRawImage(file);
    }

    public void setCameraList(java.util.List<CameraDevice> found) {
        cameras.setAll(found);
        if (!found.isEmpty()) {
            CameraDevice preferred = found.get(found.size() - 1);
            selectedCamera.set(preferred);
            if (!context.inputSource.isFrozen()) {
                pipeline.selectCamera(preferred.index());
            }
        }
    }

    public BooleanProperty frozenMutableProperty() {
        return context.inputSource.frozenProperty();
    }
}
