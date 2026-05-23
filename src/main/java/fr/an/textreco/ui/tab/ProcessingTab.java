package fr.an.textreco.ui.tab;

import fr.an.textreco.processing.EdgeDetectorProcessor;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class ProcessingTab {

    @Getter
    private final VBox root = new VBox(12);

    private final Label fpsLabel = new Label("FPS: —");
    private final Label resolutionLabel = new Label("Resolution: —");

    private long lastFrameTime = 0;

    public ProcessingTab(EdgeDetectorProcessor processor) {
        root.setPadding(new Insets(16));

        Label sectionCanny = sectionLabel("Canny Edge Detection");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        Slider threshold1Slider = new Slider(0, 300, processor.getCannyThreshold1());
        threshold1Slider.setShowTickLabels(true);
        threshold1Slider.setShowTickMarks(true);
        threshold1Slider.setMajorTickUnit(50);
        Label threshold1Value = new Label(String.valueOf((int) processor.getCannyThreshold1()));
        threshold1Slider.valueProperty().addListener((obs, o, n) -> {
            processor.setCannyThreshold1(n.doubleValue());
            threshold1Value.setText(String.valueOf(n.intValue()));
        });

        Slider threshold2Slider = new Slider(0, 500, processor.getCannyThreshold2());
        threshold2Slider.setShowTickLabels(true);
        threshold2Slider.setShowTickMarks(true);
        threshold2Slider.setMajorTickUnit(50);
        Label threshold2Value = new Label(String.valueOf((int) processor.getCannyThreshold2()));
        threshold2Slider.valueProperty().addListener((obs, o, n) -> {
            processor.setCannyThreshold2(n.doubleValue());
            threshold2Value.setText(String.valueOf(n.intValue()));
        });

        grid.add(new Label("Threshold 1 (low):"), 0, 0);
        grid.add(threshold1Slider, 1, 0);
        grid.add(threshold1Value, 2, 0);
        grid.add(new Label("Threshold 2 (high):"), 0, 1);
        grid.add(threshold2Slider, 1, 1);
        grid.add(threshold2Value, 2, 1);

        Label sectionStats = sectionLabel("Frame Stats");
        fpsLabel.setStyle("-fx-text-fill: #cccccc;");
        resolutionLabel.setStyle("-fx-text-fill: #cccccc;");

        root.getChildren().addAll(sectionCanny, grid, new Separator(), sectionStats, fpsLabel, resolutionLabel);
        root.setStyle("-fx-background-color: #1e1e1e;");
        applyLabelStyle(grid);
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }

    private void applyLabelStyle(GridPane grid) {
        grid.getChildren().stream()
                .filter(n -> n instanceof Label)
                .forEach(n -> ((Label) n).setStyle("-fx-text-fill: #cccccc;"));
    }

    public void onFrame(int width, int height) {
        long now = System.currentTimeMillis();
        if (lastFrameTime > 0) {
            long delta = now - lastFrameTime;
            double fps = delta > 0 ? 1000.0 / delta : 0;
            fpsLabel.setText(String.format("FPS: %.1f", fps));
        }
        lastFrameTime = now;
        resolutionLabel.setText("Resolution: " + width + " × " + height);
    }
}
