package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.model.FrameStats;
import fr.an.textreco.ui.viewmodel.ProcessingMonitoringViewModel;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class ProcessingMonitoringView extends VBox implements JavaView<ProcessingMonitoringViewModel> {

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
    private final Label tessOcrLabel     = statLabel("TessOCR: —");

    // exponential moving average for FPS smoothing (α = 0.1)
    private double smoothFps = 0;

    public ProcessingMonitoringView(ProcessingMonitoringViewModel viewModel) {
        viewModel.frameStatsProperty().addListener((obs, o, s) -> { if (s != null) onStats(s); });
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #1e1e1e;");

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
        addRow(statsGrid, 9, "  tess OCR",        tessOcrLabel);

        root.getChildren().addAll(sectionStats, statsGrid);
    }

    private void addRow(GridPane grid, int row, String name, Label valueLabel) {
        grid.add(rowLabel(name), 0, row);
        grid.add(valueLabel,     1, row);
    }

    private void onStats(FrameStats s) {
        smoothFps = smoothFps == 0 ? s.fps() : smoothFps * 0.9 + s.fps() * 0.1;

        resolutionLabel .setText(s.width() + " × " + s.height());
        fpsLabel        .setText(String.format("%.1f fps", smoothFps));
        totalLabel      .setText(s.totalMs()        + " ms");
        captureLabel    .setText(s.captureMs()      + " ms");
        rawConvertLabel .setText(s.rawConvertMs()   + " ms");
        edgeProcLabel   .setText(s.edgeProcessMs()  + " ms");
        edgeConvLabel   .setText(s.edgeConvertMs()  + " ms");
        perspProcLabel  .setText(s.perspProcessMs() + " ms");
        perspConvLabel  .setText(s.perspConvertMs() + " ms");
        tessOcrLabel    .setText(s.tessOcrMs() < 0 ? "—" : s.tessOcrMs() + " ms");
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
}
