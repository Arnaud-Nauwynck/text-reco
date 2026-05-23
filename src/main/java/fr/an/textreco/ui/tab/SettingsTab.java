package fr.an.textreco.ui.tab;

import fr.an.textreco.model.AppSettings;
import javafx.geometry.Insets;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class SettingsTab {

    @Getter
    private final VBox root = new VBox(12);

    @Getter
    private final Spinner<Integer> cameraIndexSpinner    = new Spinner<>(0, 9, 0);
    @Getter
    private final Spinner<Integer> captureWidthSpinner   = new Spinner<>(320, 3840, 640, 160);
    @Getter
    private final Spinner<Integer> captureHeightSpinner  = new Spinner<>(240, 2160, 480, 120);

    public SettingsTab(AppSettings appSettings) {
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #1e1e1e;");

        // --- Camera section ---
        Label sectionCamera = sectionLabel("Camera");
        cameraIndexSpinner.setEditable(true);
        captureWidthSpinner.setEditable(true);
        captureHeightSpinner.setEditable(true);

        GridPane cameraGrid = new GridPane();
        cameraGrid.setHgap(12);
        cameraGrid.setVgap(10);
        cameraGrid.add(styledLabel("Camera index:"),   0, 0); cameraGrid.add(cameraIndexSpinner,   1, 0);
        cameraGrid.add(styledLabel("Capture width:"),  0, 1); cameraGrid.add(captureWidthSpinner,   1, 1);
        cameraGrid.add(styledLabel("Capture height:"), 0, 2); cameraGrid.add(captureHeightSpinner,  1, 2);

        // --- Processing section ---
        Label sectionProcessing = sectionLabel("Processing");

        CheckBox darkThemeBox = new CheckBox("Dark theme  (white text on black background)");
        darkThemeBox.setSelected(appSettings.isDarkTheme());
        darkThemeBox.setStyle("-fx-text-fill: #cccccc;");
        darkThemeBox.selectedProperty().addListener((obs, o, n) -> appSettings.setDarkTheme(n));

        root.getChildren().addAll(sectionCamera, cameraGrid, new Separator(), sectionProcessing, darkThemeBox);
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }
}
