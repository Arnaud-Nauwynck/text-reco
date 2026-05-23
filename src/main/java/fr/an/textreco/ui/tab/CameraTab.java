package fr.an.textreco.ui.tab;

import fr.an.textreco.model.CameraDevice;
import fr.an.textreco.ui.CameraService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.Getter;

import java.io.File;
import java.util.List;

public class CameraTab {

    @Getter
    private final VBox root = new VBox(6);

    private final ImageView rawImageView       = new ImageView();
    private final ImageView processedImageView = new ImageView();

    public CameraTab(CameraService cameraService) {
        configureImageView(rawImageView);
        configureImageView(processedImageView);

        HBox toolbar = buildToolbar(cameraService);

        HBox images = new HBox(8,
                buildPanel("Camera",    rawImageView),
                buildPanel("Processed", processedImageView));
        images.setPadding(new Insets(0, 8, 8, 8));
        images.setAlignment(Pos.CENTER);

        root.getChildren().addAll(toolbar, images);
        root.setStyle("-fx-background-color: #1e1e1e;");
    }

    private HBox buildToolbar(CameraService cameraService) {
        // --- Camera selector ---
        ComboBox<CameraDevice> cameraCombo = new ComboBox<>();
        cameraCombo.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd; -fx-border-color: #555; -fx-border-width: 1;");
        cameraCombo.setPrefWidth(210);
        cameraCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CameraDevice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });
        cameraCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(CameraDevice item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Detecting…" : item.label());
            }
        });

        // Show a placeholder immediately; probe replaces it with real caps.
        CameraDevice placeholder = new CameraDevice(0, "Camera 0", 0, 0, 0);
        cameraCombo.setItems(FXCollections.observableArrayList(placeholder));
        cameraCombo.setValue(placeholder);

        // Probe all indices 0..3 via CAP_DSHOW in a background thread (can block per index).
        // CAP_DSHOW allows concurrent opens, so camera 0 being held by the service is fine.
        Thread probeThread = new Thread(() -> {
            List<CameraDevice> found = CameraService.probeAvailableCameras(3, 2000);
            Platform.runLater(() -> {
                if (found.isEmpty()) return;
                cameraCombo.setItems(FXCollections.observableArrayList(found));
                // Prefer highest index (USB enumerates after built-in)
                CameraDevice preferred = found.get(found.size() - 1);
                cameraCombo.setValue(preferred);
                cameraService.selectCamera(preferred.index());
            });
        });
        probeThread.setDaemon(true);
        probeThread.start();

        cameraCombo.setOnAction(e -> {
            CameraDevice selected = cameraCombo.getValue();
            if (selected != null) cameraService.selectCamera(selected.index());
        });

        // --- Open image file ---
        Button openBtn = toolButton("Open Image…");
        openBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open Image File");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.tiff", "*.tif"),
                    new FileChooser.ExtensionFilter("All Files", "*.*"));
            Window window = root.getScene() != null ? root.getScene().getWindow() : null;
            File file = fc.showOpenDialog(window);
            if (file != null) cameraService.loadImageFile(file);
        });

        // --- Freeze / Resume toggle ---
        ToggleButton freezeBtn = new ToggleButton("Freeze");
        styleToggleButton(freezeBtn);
        // keep button state in sync with the service (e.g. after loadImageFile auto-freezes)
        cameraService.getFrozenProperty().addListener((obs, o, frozen) -> {
            freezeBtn.setSelected(frozen);
            freezeBtn.setText(frozen ? "Resume" : "Freeze");
        });
        freezeBtn.setOnAction(e -> cameraService.toggleFreeze());

        // --- Save raw image ---
        Button saveBtn = toolButton("Save Raw…");
        saveBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Raw Image");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("PNG", "*.png"),
                    new FileChooser.ExtensionFilter("JPEG", "*.jpg"),
                    new FileChooser.ExtensionFilter("BMP", "*.bmp"));
            fc.setInitialFileName("frame.png");
            Window window = root.getScene() != null ? root.getScene().getWindow() : null;
            File file = fc.showSaveDialog(window);
            if (file != null) cameraService.saveRawImage(file);
        });

        HBox toolbar = new HBox(8, cameraCombo, openBtn, freezeBtn, saveBtn);
        toolbar.setPadding(new Insets(8, 8, 4, 8));
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setStyle("-fx-background-color: #252525; -fx-border-color: #444; -fx-border-width: 0 0 1 0;");
        return toolbar;
    }

    private void configureImageView(ImageView iv) {
        iv.setPreserveRatio(true);
        iv.setFitWidth(480);
        iv.setFitHeight(360);
    }

    private VBox buildPanel(String title, ImageView iv) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(iv);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555555; -fx-border-width: 1;");
        frame.setPadding(new Insets(4));
        VBox panel = new VBox(4, label, frame);
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }

    private static Button toolButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd; -fx-border-color: #555; -fx-border-width: 1;");
        return b;
    }

    private static void styleToggleButton(ToggleButton b) {
        b.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd; -fx-border-color: #555; -fx-border-width: 1;");
        b.selectedProperty().addListener((obs, o, selected) ->
                b.setStyle(selected
                        ? "-fx-background-color: #7a4a00; -fx-text-fill: #ffdd88; -fx-border-color: #aa7700; -fx-border-width: 1;"
                        : "-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd; -fx-border-color: #555; -fx-border-width: 1;"));
    }

    public void setRawImage(Image image) {
        rawImageView.setImage(image);
    }

    public void setProcessedImage(Image image) {
        processedImageView.setImage(image);
    }
}
