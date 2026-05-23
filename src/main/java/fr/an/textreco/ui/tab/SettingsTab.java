package fr.an.textreco.ui.tab;

import javafx.geometry.Insets;
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
    private final Spinner<Integer> cameraIndexSpinner = new Spinner<>(0, 9, 0);

    @Getter
    private final Spinner<Integer> captureWidthSpinner = new Spinner<>(320, 3840, 640, 160);

    @Getter
    private final Spinner<Integer> captureHeightSpinner = new Spinner<>(240, 2160, 480, 120);

    public SettingsTab() {
        root.setPadding(new Insets(16));

        Label sectionCamera = sectionLabel("Camera");

        cameraIndexSpinner.setEditable(true);
        captureWidthSpinner.setEditable(true);
        captureHeightSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(styledLabel("Camera index:"), 0, 0);
        grid.add(cameraIndexSpinner, 1, 0);
        grid.add(styledLabel("Capture width:"), 0, 1);
        grid.add(captureWidthSpinner, 1, 1);
        grid.add(styledLabel("Capture height:"), 0, 2);
        grid.add(captureHeightSpinner, 1, 2);

        root.getChildren().addAll(sectionCamera, grid, new Separator());
        root.setStyle("-fx-background-color: #1e1e1e;");
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
