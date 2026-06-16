package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * Shared base for the four correlation grid-detector tabs (line height, char
 * width, line offset, column offset).
 *
 * <p>Each correlation tab renders one 1-D projection (row-sums for the Y axis,
 * column-sums for the X axis) with the fitted periodic grid drawn over it, plus
 * the detected period/phase headline and the relevant tuning spinners.  This
 * base owns all the common UI and rendering; subclasses only declare which axis
 * they describe via {@link #describeAxis()}.
 */
public abstract class AbstractCorrelationAxisView extends BorderPane
        implements JavaView<GridDetectViewModel> {

    /** Per-axis configuration supplied by the concrete tabs. */
    protected record AxisSpec(
            String title,           // panel heading
            String periodName,      // e.g. "lineH" / "charW"
            String offsetName,      // e.g. "y0" / "x0"
            String unitsName,       // e.g. "lines" / "cols"
            Color barColor,
            Color gridColor) {
    }

    private static final double PROJ_W = 760;
    private static final double PROJ_H = 220;

    @Getter
    private final BorderPane root = this;

    private final AxisSpec spec;

    private final Label periodLabel = statLabel();
    private final Label offsetLabel = statLabel();
    private final Label countLabel = statLabel();
    private final Canvas projCanvas = new Canvas(PROJ_W, PROJ_H);

    protected AbstractCorrelationAxisView(GridDetectViewModel viewModel) {
        this.spec = describeAxis();

        VBox controls = buildControls(viewModel);

        projCanvas.setStyle("-fx-background-color: #0f0f14;");
        VBox projPanel = buildPanel("Projection + grid fit", projCanvas);

        VBox content = new VBox(8, controls, projPanel);
        content.setPadding(new Insets(10));
        content.setStyle("-fx-background-color: #1e1e1e;");
        VBox.setVgrow(projPanel, Priority.ALWAYS);

        setCenter(content);
        setStyle("-fx-background-color: #1e1e1e;");

        viewModel.correlationGridDetectionProperty()
                .addListener((obs, o, r) -> onResult(r));
        onResult(viewModel.correlationGridDetectionProperty().get());
    }

    // -------------------------------------------------------------------------
    // subclass hooks
    // -------------------------------------------------------------------------

    /** Static description of the axis this tab renders. */
    protected abstract AxisSpec describeAxis();

    /** Period (line height / char width) detected this frame. */
    protected abstract double period(CorrelationGridDetectionResult r);

    /** Gap phase (line / column offset) detected this frame. */
    protected abstract double phase(CorrelationGridDetectionResult r);

    /** Projection histogram for this axis. */
    protected abstract double[] projection(CorrelationGridDetectionResult r);

    /** Detected cell positions (line tops / column centres) for this axis. */
    protected abstract List<Integer> positions(CorrelationGridDetectionResult r);

    /** Search-range spinner range model: {min, max, low, high} property pair. */
    protected abstract IntegerProperty minRangeProperty(GridDetectViewModel vm);

    protected abstract IntegerProperty maxRangeProperty(GridDetectViewModel vm);

    /**
     * Enable/value properties for forcing this tab's primary quantity (the one
     * shown in the headline: period for the *Height/*Width tabs, phase for the
     * *Offset tabs).  Return {@code null} from either to omit the force row.
     */
    protected abstract BooleanProperty forceEnabledProperty(GridDetectViewModel vm);

    protected abstract DoubleProperty forcedValueProperty(GridDetectViewModel vm);

    // -------------------------------------------------------------------------
    // build
    // -------------------------------------------------------------------------

    private VBox buildControls(GridDetectViewModel viewModel) {
        Label heading = new Label(spec.title());
        heading.setFont(Font.font("System", FontWeight.BOLD, 15));
        heading.setStyle("-fx-text-fill: #aaaaff;");

        Spinner<Integer> minSp = intSpinner(1, 400);
        Spinner<Integer> maxSp = intSpinner(1, 400);
        bindIntSpinner(minSp, minRangeProperty(viewModel));
        bindIntSpinner(maxSp, maxRangeProperty(viewModel));

        Spinner<Double> alphaSp = dblSpinner(0.01, 1.0);
        bindDblSpinner(alphaSp, viewModel.getCorrelationGridDetectorSettings().smoothingAlpha);

        HBox paramRow = hrow(
                styledLabel("min:"), minSp,
                styledLabel("max:"), maxSp,
                styledLabel("alpha:"), alphaSp);

        VBox box = new VBox(6, heading, periodLabel, offsetLabel, countLabel, paramRow);

        HBox forceRow = buildForceRow(viewModel);
        if (forceRow != null) box.getChildren().add(forceRow);

        // reusable shared grid-coordinate controls (line height / char width /
        // line offset / char offset), bound to the model shared across the Char
        // Classifier / Grid Detect / correlation-axis tabs.
        box.getChildren().add(new GridDetectCoordView(viewModel.getGridForcedValues()));

        return box;
    }

    /**
     * Optional "force value" row (checkbox + spinner).  When the checkbox is
     * ticked, the detector uses the spinner value instead of its computed
     * estimate.  Returns {@code null} when the tab does not expose a forced
     * quantity.
     */
    private HBox buildForceRow(GridDetectViewModel viewModel) {
        BooleanProperty enabled = forceEnabledProperty(viewModel);
        DoubleProperty value = forcedValueProperty(viewModel);
        if (enabled == null || value == null) return null;

        CheckBox cb = new CheckBox("Force");
        cb.setStyle("-fx-text-fill: #cccccc;");
        cb.selectedProperty().bindBidirectional(enabled);

        Spinner<Double> valueSp = dblSpinner(0.0, 500.0);
        bindDblSpinner(valueSp, value);
        valueSp.disableProperty().bind(enabled.not());

        return hrow(cb, valueSp, styledLabel("px"));
    }

    // -------------------------------------------------------------------------
    // render
    // -------------------------------------------------------------------------

    private void onResult(CorrelationGridDetectionResult r) {
        if (r == null) {
            periodLabel.setText(spec.periodName() + ": —");
            offsetLabel.setText(spec.offsetName() + ": —");
            countLabel.setText(spec.unitsName() + ": —");
            clearCanvas();
            return;
        }
        double T = period(r);
        double phi = phase(r);
        int count = positions(r).size();
        periodLabel.setText(String.format(Locale.ROOT, "%s: %.2f px", spec.periodName(), T));
        offsetLabel.setText(String.format(Locale.ROOT, "%s: %.2f px", spec.offsetName(), phi));
        countLabel.setText(String.format(Locale.ROOT, "%s: %d", spec.unitsName(), count));
        drawProjectionWithGrid(projection(r), T, phi);
    }

    private void clearCanvas() {
        GraphicsContext gc = projCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(15, 15, 20));
        gc.fillRect(0, 0, projCanvas.getWidth(), projCanvas.getHeight());
    }

    private void drawProjectionWithGrid(double[] proj, double period, double phase) {
        GraphicsContext gc = projCanvas.getGraphicsContext2D();
        double cw = projCanvas.getWidth(), ch = projCanvas.getHeight();
        gc.setFill(Color.rgb(15, 15, 20));
        gc.fillRect(0, 0, cw, ch);
        if (proj == null || proj.length == 0) return;

        double maxV = 1.0;
        for (double v : proj) if (v > maxV) maxV = v;

        int n = proj.length;
        double xScale = cw / n;

        // projection bars
        gc.setFill(spec.barColor());
        for (int i = 0; i < n; i++) {
            double bh = (proj[i] / maxV) * (ch - 2);
            gc.fillRect(i * xScale, ch - bh, Math.max(1.0, xScale - 0.3), bh);
        }

        if (period > 0) {
            // gap grid lines: phase + N·period
            gc.setStroke(Color.rgb(255, 80, 80, 0.9));
            gc.setLineWidth(1.5);
            for (double pos = phase; pos < n; pos += period) {
                double x = pos * xScale;
                gc.strokeLine(x, 0, x, ch);
            }
            // cell centres: half a period off the gap phase
            gc.setStroke(spec.gridColor());
            gc.setLineWidth(1.0);
            double cellStart = (phase + period * 0.5) % period;
            for (double pos = cellStart; pos < n; pos += period) {
                double x = pos * xScale;
                gc.strokeLine(x, ch * 0.3, x, ch);
            }
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 11));
            gc.setFill(Color.rgb(255, 220, 220, 0.95));
            gc.fillText(String.format(Locale.ROOT, "%s=%.2f  %s(φ)=%.2f",
                    spec.periodName(), period, spec.offsetName(), phase), 4, 13);
        }
    }

    // -------------------------------------------------------------------------
    // small UI helpers (mirrors GridDetectView styling)
    // -------------------------------------------------------------------------

    private static VBox buildPanel(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555; -fx-border-width: 1;");
        frame.setPadding(new Insets(3));
        VBox panel = new VBox(3, label, frame);
        panel.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(frame, Priority.ALWAYS);
        return panel;
    }

    private static Label statLabel() {
        Label l = new Label();
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace; -fx-font-size: 13;");
        return l;
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(6, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private static Spinner<Integer> intSpinner(int min, int max) {
        Spinner<Integer> s = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, min));
        s.setEditable(true);
        s.setPrefWidth(80);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return s;
    }

    private static Spinner<Double> dblSpinner(double min, double max) {
        Spinner<Double> s = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, min, 0.05));
        s.setEditable(true);
        s.setPrefWidth(90);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        ((SpinnerValueFactory.DoubleSpinnerValueFactory) s.getValueFactory())
                .setConverter(new javafx.util.StringConverter<Double>() {
                    @Override
                    public String toString(Double v) {
                        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
                    }

                    @Override
                    public Double fromString(String s) {
                        if (s == null || s.isBlank()) return 0.0;
                        return Double.parseDouble(s.trim().replace(',', '.'));
                    }
                });
        return s;
    }

    private static void bindIntSpinner(Spinner<Integer> s, IntegerProperty prop) {
        s.getValueFactory().setValue(prop.get());
        s.valueProperty().addListener((obs, o, n) -> {
            if (n != null) prop.set(n);
        });
        prop.addListener((obs, o, n) -> {
            if (n.intValue() != s.getValue()) s.getValueFactory().setValue(n.intValue());
        });
    }

    private static void bindDblSpinner(Spinner<Double> s, DoubleProperty prop) {
        s.getValueFactory().setValue(prop.get());
        s.valueProperty().addListener((obs, o, n) -> {
            if (n != null) prop.set(n);
        });
        prop.addListener((obs, o, n) -> {
            if (Math.abs(n.doubleValue() - s.getValue()) > 0.001)
                s.getValueFactory().setValue(n.doubleValue());
        });
    }
}
