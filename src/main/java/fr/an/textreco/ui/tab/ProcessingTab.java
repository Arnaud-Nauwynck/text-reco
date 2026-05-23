package fr.an.textreco.ui.tab;

import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.model.FrameStats;
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

    // stats labels
    private final Label fpsLabel         = statLabel("FPS: —");
    private final Label resolutionLabel  = statLabel("Resolution: —");
    private final Label totalLabel       = statLabel("Total: —");
    private final Label captureLabel     = statLabel("Capture: —");
    private final Label rawConvertLabel  = statLabel("Raw→RGB: —");
    private final Label edgeProcLabel    = statLabel("Edge detect: —");
    private final Label edgeConvLabel    = statLabel("Edge→RGB: —");
    private final Label perspProcLabel   = statLabel("Perspective warp: —");
    private final Label perspConvLabel   = statLabel("Persp→RGB: —");

    // exponential moving average for FPS smoothing (α = 0.1)
    private double smoothFps = 0;

    public ProcessingTab(EdgeDetectorSettings settings) {
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #1e1e1e;");

        // --- Canny sliders ---
        Label sectionCanny = sectionLabel("Canny Edge Detection");
        GridPane sliderGrid = buildSliderGrid(settings);

        // --- Timing table ---
        Label sectionStats = sectionLabel("Frame Stats");

        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(20);
        statsGrid.setVgap(6);

        addRow(statsGrid, 0, "Resolution",       resolutionLabel);
        addRow(statsGrid, 1, "FPS (smoothed)",    fpsLabel);
        addRow(statsGrid, 2, "Total frame time",  totalLabel);
        addRow(statsGrid, 3, "  capture.read",    captureLabel);
        addRow(statsGrid, 4, "  raw → RGB",       rawConvertLabel);
        addRow(statsGrid, 5, "  edge detect",     edgeProcLabel);
        addRow(statsGrid, 6, "  edge → RGB",      edgeConvLabel);
        addRow(statsGrid, 7, "  persp warp",      perspProcLabel);
        addRow(statsGrid, 8, "  persp → RGB",     perspConvLabel);

        root.getChildren().addAll(
                sectionCanny, sliderGrid,
                new Separator(),
                sectionStats, statsGrid
        );
    }

    private GridPane buildSliderGrid(EdgeDetectorSettings settings) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        Label val1 = statLabel(String.valueOf((int) settings.getCannyThreshold1()));
        Slider s1 = slider(0, 300, settings.getCannyThreshold1(), 50);
        s1.valueProperty().addListener((obs, o, n) -> {
            settings.setCannyThreshold1(n.doubleValue());
            val1.setText(String.valueOf(n.intValue()));
        });

        Label val2 = statLabel(String.valueOf((int) settings.getCannyThreshold2()));
        Slider s2 = slider(0, 500, settings.getCannyThreshold2(), 50);
        s2.valueProperty().addListener((obs, o, n) -> {
            settings.setCannyThreshold2(n.doubleValue());
            val2.setText(String.valueOf(n.intValue()));
        });

        grid.add(rowLabel("Threshold 1 (low):"),  0, 0); grid.add(s1, 1, 0); grid.add(val1, 2, 0);
        grid.add(rowLabel("Threshold 2 (high):"), 0, 1); grid.add(s2, 1, 1); grid.add(val2, 2, 1);
        return grid;
    }

    private void addRow(GridPane grid, int row, String name, Label valueLabel) {
        grid.add(rowLabel(name), 0, row);
        grid.add(valueLabel,     1, row);
    }

    public void onStats(FrameStats s) {
        smoothFps = smoothFps == 0 ? s.fps() : smoothFps * 0.9 + s.fps() * 0.1;

        resolutionLabel .setText(s.width() + " × " + s.height());
        fpsLabel        .setText(String.format("%.1f fps", smoothFps));
        totalLabel      .setText(s.totalMs()       + " ms");
        captureLabel    .setText(s.captureMs()     + " ms");
        rawConvertLabel .setText(s.rawConvertMs()  + " ms");
        edgeProcLabel   .setText(s.edgeProcessMs() + " ms");
        edgeConvLabel   .setText(s.edgeConvertMs() + " ms");
        perspProcLabel  .setText(s.perspProcessMs()+ " ms");
        perspConvLabel  .setText(s.perspConvertMs()+ " ms");
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }

    private static Label rowLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #aaaaaa;");
        return l;
    }

    private static Label statLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return l;
    }

    private static Slider slider(double min, double max, double value, double tickUnit) {
        Slider s = new Slider(min, max, value);
        s.setShowTickLabels(true);
        s.setShowTickMarks(true);
        s.setMajorTickUnit(tickUnit);
        return s;
    }
}
