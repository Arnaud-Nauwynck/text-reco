package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.model.CorrelationGridDetectorSettings;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.GridDetectorMode;
import fr.an.textreco.model.GridDetectCoordModel;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.model.GridDetectorSettings;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

import java.util.List;

public class GridDetectView extends HBox implements JavaView<GridDetectViewModel> {

    private static final double PREVIEW_W = 520;
    private static final double PREVIEW_H = 400;
    private static final double HIST_W = 120;
    private static final double VHIST_H = 80;
    private static final double ACC_W = 300;
    private static final double ACC_H = 90;

    @Getter
    private final HBox root = new HBox(8);

    // --- left panel ---
    private final ImageView warpedView = new ImageView();
    private final Canvas overlay = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Canvas histCanvas = new Canvas(HIST_W, PREVIEW_H);
    private final Canvas vHistCanvas = new Canvas(PREVIEW_W, VHIST_H);

    // --- right panel ---
    private final Label lineCountLabel = statLabel("Lines: —");
    private final Label gridLabel = statLabel("Grid: —");
    private final Label lineHLabel = statLabel("lineH: —");
    private final Label charWLabel = statLabel("charW: —");

    // diff-histograms (gap frequency)
    private final Canvas diffHistYCanvas = new Canvas(ACC_W, ACC_H);
    private final Canvas diffHistXCanvas = new Canvas(ACC_W, ACC_H);

    // Hough offset charts
    private final Canvas accY0Canvas = new Canvas(ACC_W, ACC_H);
    private final Canvas accX0Canvas = new Canvas(ACC_W, ACC_H);

    private final VBox lineList = new VBox(3);
    private final ScrollPane lineScroll;

    // --- forced periods ---
    private final CheckBox forceLineHCb = styledCheckBox("Force lineH");
    private final Spinner<Double> forcedLineHSp = dblSpinner(1.0, 200.0, 28.0);
    private final CheckBox forceCharWCb = styledCheckBox("Force charW");
    private final Spinner<Double> forcedCharWRatioSp = dblSpinner(0.1, 20.0, 2.0);
    private final Spinner<Double> forcedCharWPxSp = dblSpinner(0.1, 200.0, 15.0);

    // --- forced offsets ---
    private final CheckBox forceLineY0Cb = styledCheckBox("Force y0");
    private final Spinner<Double> forcedLineY0Sp = dblSpinner(0.0, 500.0, 0.0);
    private final CheckBox forceCharX0Cb = styledCheckBox("Force x0");
    private final Spinner<Double> forcedCharX0Sp = dblSpinner(0.0, 500.0, 0.0);

    // When line height or offset is forced, optionally also overlay the grid the
    // detector *would* have computed automatically, so the forced and computed
    // line positions can be compared on the image.
    private final CheckBox showComputedLinesCb = styledCheckBox("Show computed lines");

    // --- forced line / column counts ---
    private final Label detectedLineCountLabel = statLabel("Lines detected: —");
    private final Label detectedColCountLabel = statLabel("Cols  detected: —");
    private final CheckBox forceLineCountCb = styledCheckBox("Force line count");
    private final Spinner<Integer> forcedLineCountSp = intSpinner(1, 500, 24);
    private final CheckBox forceColCountCb = styledCheckBox("Force col count");
    private final Spinner<Integer> forcedColCountSp = intSpinner(1, 500, 80);

    // --- mode selector ---
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton valleyRadio = styledRadio("Valley / Hough", modeGroup);
    private final RadioButton corrRadio = styledRadio("Correlation", modeGroup);

    // --- shared "forced value" controls (line height / char width / line offset
    //     / char offset) — reusable GridDetectCoordView bound to the shared model ---
    private final GridDetectCoordModel forcedValues;
    private GridDetectCoordView coordView;

    private GridDetectionResult lastGrid = null;
    private PreProcessingResult lastPreProc = null;
    private TextLineExtractionResult lastResult = null;

    // detector settings (read in redrawOverlay to decide whether forcing is active)
    private GridDetectorSettings gridSettings;

    public GridDetectView(GridDetectViewModel viewModel) {
        this.forcedValues = viewModel.getGridForcedValues();
        // The processors read the detector-settings models every frame; the
        // shared GridDetectCoordModel is display-only, so push its force/value
        // changes into the active detector's settings to trigger a reprocess.
        bridgeForcedValuesToSettings(viewModel);
        viewModel.preProcessingProperty().addListener((obs, o, r) -> onPreProcessing(r));
        viewModel.textLinesProperty().addListener((obs, o, r) -> {
            if (r != null) onResult(r);
        });
        viewModel.gridDetectionProperty().addListener((obs, o, r) -> {
            lastGrid = r;
            onGrid(r);
        });

        // mode radio buttons wired to model
        valleyRadio.setSelected(viewModel.gridDetectorMode().get() == GridDetectorMode.VALLEY);
        corrRadio.setSelected(viewModel.gridDetectorMode().get() == GridDetectorMode.CORRELATION);
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == corrRadio) viewModel.gridDetectorMode().set(GridDetectorMode.CORRELATION);
            else viewModel.gridDetectorMode().set(GridDetectorMode.VALLEY);
        });
        viewModel.gridDetectorMode().addListener((obs, o, n) -> {
            valleyRadio.setSelected(n == GridDetectorMode.VALLEY);
            corrRadio.setSelected(n == GridDetectorMode.CORRELATION);
        });

        GridDetectorSettings gs = viewModel.getGridDetectorSettings();
        this.gridSettings = gs;

        forceLineHCb.selectedProperty().bindBidirectional(gs.forceLineH);
        bindDblSpinner(forcedLineHSp, gs.forcedLineH);
        forcedLineHSp.disableProperty().bind(gs.forceLineH.not());

        forceCharWCb.selectedProperty().bindBidirectional(gs.forceCharWidth);
        bindDblSpinner(forcedCharWRatioSp, gs.forcedCharWRatio);
        bindDblSpinner(forcedCharWPxSp, gs.forcedCharWPx);
        forcedCharWRatioSp.disableProperty().bind(gs.forceCharWidth.not());
        forcedCharWPxSp.disableProperty().bind(gs.forceCharWidth.not());
        // keep px = lineH / ratio when ratio changes (convenience sync, one-way)
        gs.forcedCharWRatio.addListener((obs, o, ratio) -> {
            double lineH = gs.forcedLineH.get();
            if (ratio.doubleValue() > 0) gs.forcedCharWPx.set(lineH / ratio.doubleValue());
        });
        gs.forcedCharWPx.addListener((obs, o, px) -> {
            double lineH = gs.forcedLineH.get();
            if (px.doubleValue() > 0) gs.forcedCharWRatio.set(lineH / px.doubleValue());
        });

        forceLineY0Cb.selectedProperty().bindBidirectional(gs.forceLineY0);
        bindDblSpinner(forcedLineY0Sp, gs.forcedLineY0);
        forcedLineY0Sp.disableProperty().bind(gs.forceLineY0.not());

        // "Show computed lines" is only meaningful while the line grid is forced.
        showComputedLinesCb.visibleProperty().bind(gs.forceLineH.or(gs.forceLineY0));
        showComputedLinesCb.managedProperty().bind(showComputedLinesCb.visibleProperty());
        // redraw the overlay when toggled (re-using the last extraction result)
        showComputedLinesCb.selectedProperty().addListener((o, a, b) -> {
            if (lastResult != null) redrawOverlay(lastResult);
        });

        forceCharX0Cb.selectedProperty().bindBidirectional(gs.forceCharX0);
        bindDblSpinner(forcedCharX0Sp, gs.forcedCharX0);
        forcedCharX0Sp.disableProperty().bind(gs.forceCharX0.not());

        forceLineCountCb.selectedProperty().bindBidirectional(gs.forceLineCount);
        bindIntSpinner(forcedLineCountSp, gs.forcedLineCount);
        forcedLineCountSp.disableProperty().bind(gs.forceLineCount.not());

        forceColCountCb.selectedProperty().bindBidirectional(gs.forceColCount);
        bindIntSpinner(forcedColCountSp, gs.forcedColCount);
        forcedColCountSp.disableProperty().bind(gs.forceColCount.not());

        warpedView.setPreserveRatio(true);
        warpedView.setFitWidth(PREVIEW_W);
        warpedView.setFitHeight(PREVIEW_H);
        overlay.setMouseTransparent(true);

        StackPane previewStack = new StackPane(warpedView, overlay);
        previewStack.setAlignment(Pos.TOP_LEFT);
        previewStack.setMaxSize(PREVIEW_W, PREVIEW_H);

        histCanvas.setStyle("-fx-background-color: #1a1a1a;");
        vHistCanvas.setStyle("-fx-background-color: #1a1a1a;");

        HBox previewAndHist = new HBox(2, previewStack, histCanvas);
        previewAndHist.setAlignment(Pos.TOP_LEFT);
        VBox leftContent = new VBox(2, previewAndHist, vHistCanvas);
        VBox leftPanel = buildPanel("Binary + histograms", leftContent);

        // right: diff-histograms + offset charts in 2×2
        HBox chartRow1 = new HBox(8,
                buildPanel("Gap freq lineH (→ period)", diffHistYCanvas),
                buildPanel("Gap freq charW (→ period)", diffHistXCanvas));
        HBox chartRow2 = new HBox(8,
                buildPanel("Y offset (Hough min)", accY0Canvas),
                buildPanel("X offset (Hough min)", accX0Canvas));
        chartRow1.setAlignment(Pos.TOP_LEFT);
        chartRow2.setAlignment(Pos.TOP_LEFT);

        HBox forceLineHRow = hrow(forceLineHCb, forcedLineHSp, styledLabel("px"));
        HBox forceCharWRow = hrow(forceCharWCb,
                styledLabel("lineH/charW ="), forcedCharWRatioSp,
                styledLabel("  charW ="), forcedCharWPxSp, styledLabel("px"));
        HBox forceOffsetRow = hrow(
                forceLineY0Cb, forcedLineY0Sp, styledLabel("px   "),
                forceCharX0Cb, forcedCharX0Sp, styledLabel("px"));
        HBox showComputedRow = hrow(showComputedLinesCb);
        HBox forceLineCountRow = hrow(detectedLineCountLabel, forceLineCountCb, forcedLineCountSp);
        HBox forceColCountRow = hrow(detectedColCountLabel, forceColCountCb, forcedColCountSp);

        VBox valleySettingsBox = new VBox(4,
                lineCountLabel, gridLabel, lineHLabel, charWLabel,
                forceLineHRow, forceCharWRow, forceOffsetRow, showComputedRow,
                forceLineCountRow, forceColCountRow,
                chartRow1, chartRow2);

        // In CORRELATION mode the detailed views live in their own tabs
        // (Line Height / Char Width / Line Offset / Column Offset).
        Label corrHint = statLabel(
                "Correlation mode — see the Line Height / Char Width /\n"
                + "Line Offset / Column Offset tabs for details.");
        corrHint.setWrapText(true);

        lineScroll = new ScrollPane(lineList);
        lineScroll.setFitToWidth(true);
        lineScroll.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
        lineList.setPadding(new Insets(4));

        HBox modeRow = hrow(styledLabel("Detector:"), valleyRadio, corrRadio);

        // shared forced-value controls (reusable component, bound to the model
        // shared with the Char Classifier / correlation-axis tabs)
        coordView = new GridDetectCoordView(forcedValues);

        VBox rightContent = new VBox(6,
                modeRow,
                valleySettingsBox,
                corrHint,
                sectionLabel("Forced grid values"), coordView,
                sectionLabel("Extracted Lines"), lineScroll);
        VBox.setVgrow(lineScroll, javafx.scene.layout.Priority.ALWAYS);
        rightContent.setStyle("-fx-background-color: #1e1e1e;");

        // show/hide the detector-specific panels based on mode
        corrHint.visibleProperty().bind(viewModel.gridDetectorMode().isEqualTo(GridDetectorMode.CORRELATION));
        corrHint.managedProperty().bind(corrHint.visibleProperty());
        valleySettingsBox.visibleProperty().bind(viewModel.gridDetectorMode().isEqualTo(GridDetectorMode.VALLEY));
        valleySettingsBox.managedProperty().bind(valleySettingsBox.visibleProperty());

        VBox rightPanel = buildPanel("Lines & Grid", rightContent);

        root.getChildren().addAll(leftPanel, rightPanel);
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.TOP_LEFT);
    }

    // -------------------------------------------------------------------------
    // forced-value bridge: shared display model -> active detector settings
    // -------------------------------------------------------------------------

    /**
     * Pushes force/value changes from the shared {@link GridDetectCoordModel}
     * (driven by the {@link GridDetectCoordView} controls) into the detector
     * settings the processors actually read each frame, so editing a forced
     * value reprocesses the frame and redraws all panels.
     *
     * <p>Writes only into the settings of the {@link GridDetectorMode#CORRELATION
     * correlation} or {@link GridDetectorMode#VALLEY valley} detector that is
     * currently active, so the two detectors' independent settings stay
     * decoupled. Re-applies on a mode switch so the newly active detector picks
     * up the current forced values.
     */
    private void bridgeForcedValuesToSettings(GridDetectViewModel viewModel) {
        CorrelationGridDetectorSettings corr = viewModel.getCorrelationGridDetectorSettings();
        GridDetectorSettings valley = viewModel.getGridDetectorSettings();
        ObjectProperty<GridDetectorMode> mode = viewModel.gridDetectorMode();

        Runnable apply = () -> applyForcedValues(forcedValues, corr, valley, mode.get());
        forcedValues.lineHeight.force.addListener((o, a, b) -> apply.run());
        forcedValues.lineHeight.forcedValue.addListener((o, a, b) -> apply.run());
        forcedValues.charWidth.force.addListener((o, a, b) -> apply.run());
        forcedValues.charWidth.forcedValue.addListener((o, a, b) -> apply.run());
        forcedValues.lineOffset.force.addListener((o, a, b) -> apply.run());
        forcedValues.lineOffset.forcedValue.addListener((o, a, b) -> apply.run());
        forcedValues.charOffset.force.addListener((o, a, b) -> apply.run());
        forcedValues.charOffset.forcedValue.addListener((o, a, b) -> apply.run());
        mode.addListener((o, a, b) -> apply.run());

        // seed the active detector with any forced values already set at startup
        apply.run();
    }

    private static void applyForcedValues(GridDetectCoordModel src,
                                          CorrelationGridDetectorSettings corr,
                                          GridDetectorSettings valley,
                                          GridDetectorMode mode) {
        if (mode == GridDetectorMode.CORRELATION) {
            corr.forceLineHeight.set(src.lineHeight.force.get());
            corr.forcedLineHeight.set(src.lineHeight.forcedValue.get());
            corr.forceCharWidth.set(src.charWidth.force.get());
            corr.forcedCharWidth.set(src.charWidth.forcedValue.get());
            corr.forceLineOffset.set(src.lineOffset.force.get());
            corr.forcedLineOffset.set(src.lineOffset.forcedValue.get());
            corr.forceColOffset.set(src.charOffset.force.get());
            corr.forcedColOffset.set(src.charOffset.forcedValue.get());
        } else {
            valley.forceLineH.set(src.lineHeight.force.get());
            valley.forcedLineH.set(src.lineHeight.forcedValue.get());
            valley.forceCharWidth.set(src.charWidth.force.get());
            valley.forcedCharWPx.set(src.charWidth.forcedValue.get());
            valley.forceLineY0.set(src.lineOffset.force.get());
            valley.forcedLineY0.set(src.lineOffset.forcedValue.get());
            valley.forceCharX0.set(src.charOffset.force.get());
            valley.forcedCharX0.set(src.charOffset.forcedValue.get());
        }
    }

    // -------------------------------------------------------------------------
    // event handlers
    // -------------------------------------------------------------------------

    private void onResult(TextLineExtractionResult result) {
        lastResult = result;
        redrawOverlay(result);
        redrawHHistogram(result);
        rebuildLineList(result);
        int detectedLines = result.lines().size();
        lineCountLabel.setText("Lines: " + detectedLines
                + "  (" + result.frameWidth() + "×" + result.frameHeight() + ")");
        detectedLineCountLabel.setText("Lines detected: " + detectedLines);
    }

    private void setWarpedImage(Image image) {
        warpedView.setImage(image);
    }

    private void onGrid(GridDetectionResult r) {
        if (r == null) {
            gridLabel.setText("Grid: —");
            detectedColCountLabel.setText("Cols  detected: —");
            return;
        }
        // publish computed grid values to the shared models so every tab's
        // GridDetectCoordView shows the auto-detected value next to its override.
        coordView.updateComputed(r);
        int detectedCols = r.bestCharW() > 0 && lastPreProc != null
                ? (int) Math.round(lastPreProc.frameWidth() / r.bestCharW())
                : r.vValleysFiltered().length;
        detectedColCountLabel.setText("Cols  detected: " + detectedCols);
        gridLabel.setText(String.format("x0=%.2f  y0=%.2f  charW=%.2f  lineH=%.2f",
                r.bestCharX0(), r.bestLineY0(), r.bestCharW(), r.bestLineH()));
        lineHLabel.setText(String.format("lineH: %.2fpx  y0=%.2f", r.bestLineH(), r.bestLineY0())
                + "  (" + r.hValleys().length + " valleys"
                + (r.hValleysFiltered().length != r.hValleys().length
                ? "  →" + r.hValleysFiltered().length + " on-grid" : "") + ")"
                + valleySummary(r.hValleysFiltered()));
        charWLabel.setText(String.format("charW: %.2fpx  x0=%.2f", r.bestCharW(), r.bestCharX0())
                + "  (" + r.vValleys().length + " valleys"
                + (r.vValleysFiltered().length != r.vValleys().length
                ? "  →" + r.vValleysFiltered().length + " on-grid" : "") + ")"
                + valleySummary(r.vValleysFiltered()));
        drawDiffHist(diffHistYCanvas, r.diffHistY(), r.minLineH(), r.bestLineH(),
                "lineH", Color.rgb(0, 200, 255, 0.9));
        drawDiffHist(diffHistXCanvas, r.diffHistX(), r.minCharW(), r.bestCharW(),
                "charW", Color.rgb(255, 200, 0, 0.9));
        int bestYTi = (int) Math.round(r.bestLineH()) - r.minLineH();
        drawOffsetChart(accY0Canvas, accOffsetSlice(r.accY(), bestYTi, (int) Math.round(r.bestLineH())),
                r.bestLineY0(), "y0", Color.rgb(0, 200, 255, 0.9));
        int bestXTi = (int) Math.round(r.bestCharW()) - r.minCharW();
        drawOffsetChart(accX0Canvas, accOffsetSlice(r.accX(), bestXTi, (int) Math.round(r.bestCharW())),
                r.bestCharX0(), "x0", Color.rgb(255, 200, 0, 0.9));
        // also refresh histograms that show valley marks
        if (lastPreProc != null) drawVHistogram(lastPreProc, r);
    }

    private void onPreProcessing(PreProcessingResult r) {
        if (r == null) return;
        lastPreProc = r;
        setWarpedImage(r.binaryImage());
        drawVHistogram(r, lastGrid);
    }

    // -------------------------------------------------------------------------
    // diff-histogram: gap frequency between consecutive valleys
    // -------------------------------------------------------------------------

    private static void drawDiffHist(Canvas canvas, int[] hist, int minT,
                                     double bestT, String axisName, Color barColor) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        gc.setFill(Color.rgb(15, 15, 20));
        gc.fillRect(0, 0, cw, ch);
        if (hist == null || hist.length == 0) return;

        int maxCount = 1;
        for (int c : hist) if (c > maxCount) maxCount = c;

        int n = hist.length;
        double bw = cw / n;
        double markedFrac = (bestT > 0 && bestT >= minT) ? bestT - minT : -1;

        for (int i = 0; i < n; i++) {
            double bh = ((double) hist[i] / maxCount) * (ch - 14);
            boolean isMarked = markedFrac >= 0 && Math.abs(i - markedFrac) < 0.6;
            gc.setFill(isMarked ? Color.rgb(255, 80, 80, 0.9) : barColor.deriveColor(0, 1, 1, 0.7));
            gc.fillRect(i * bw, ch - bh - 12, Math.max(1, bw - 0.5), bh);
            if (hist[i] > 0) {
                gc.setFill(Color.rgb(200, 200, 200, 0.8));
                gc.setFont(Font.font("Monospace", 8));
                gc.fillText(String.valueOf(hist[i]), i * bw + 1, ch - 13);
            }
        }

        gc.setFont(Font.font("Monospace", 8));
        gc.setFill(Color.rgb(150, 150, 150, 0.9));
        gc.fillText(String.valueOf(minT), 1, ch - 1);
        gc.fillText(String.valueOf(minT + n - 1), cw - 22, ch - 1);
        if (markedFrac >= 0) {
            double px = markedFrac * bw + bw * 0.5;
            gc.setStroke(Color.rgb(255, 60, 60, 0.9));
            gc.setLineWidth(1.5);
            gc.strokeLine(px, 0, px, ch - 12);
            gc.setFill(Color.rgb(255, 220, 220, 0.9));
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            gc.fillText(String.format("%s=%.2f", axisName, bestT), Math.min(px + 2, cw - 55), 10);
        }
    }

    // -------------------------------------------------------------------------
    // Hough offset chart (minimum bin = gap phase)
    // -------------------------------------------------------------------------

    private static void drawOffsetChart(Canvas canvas, float[] votes, double markedFrac,
                                        String axisName, Color barColor) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        gc.setFill(Color.rgb(15, 15, 20));
        gc.fillRect(0, 0, cw, ch);
        if (votes == null || votes.length == 0) return;

        float maxV = 1f;
        for (float v : votes) if (v > maxV) maxV = v;

        int n = votes.length;
        double bw = cw / n;
        for (int i = 0; i < n; i++) {
            double bh = (votes[i] / maxV) * (ch - 2);
            boolean isMarked = markedFrac >= 0 && Math.abs(i - markedFrac) < 0.6;
            gc.setFill(isMarked ? Color.rgb(255, 80, 80, 0.85) : barColor);
            gc.fillRect(i * bw, ch - bh, Math.max(1, bw - 0.5), bh);
        }
        if (markedFrac >= 0 && markedFrac < n) {
            double px = markedFrac * bw + bw * 0.5;
            gc.setStroke(Color.rgb(255, 60, 60, 0.95));
            gc.setLineWidth(1.5);
            gc.strokeLine(px, 0, px, ch);
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            gc.setFill(Color.rgb(255, 220, 220, 0.9));
            gc.fillText(String.format("%s=%.2f (min)", axisName, markedFrac), Math.min(px + 3, cw - 70), 10);
        }
    }

    private static float[] accOffsetSlice(float[][] acc, int ti, int period) {
        if (ti < 0 || ti >= acc.length) return new float[0];
        float[] s = new float[period];
        for (int o = 0; o < period && o < acc[ti].length; o++) s[o] = acc[ti][o];
        return s;
    }

    // -------------------------------------------------------------------------
    // V-histogram: vertical projection with raw + filtered valley marks
    // -------------------------------------------------------------------------

    private void drawVHistogram(PreProcessingResult r, GridDetectionResult grid) {
        GraphicsContext gc = vHistCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, PREVIEW_W, VHIST_H);

        float[] sums = r.vColSums();
        int fw = r.frameWidth(), fh = r.frameHeight();
        if (sums == null || sums.length == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale;
        double offX = (PREVIEW_W - rendW) / 2.0;

        float maxV = 1f;
        for (float v : sums) if (v > maxV) maxV = v;

        // bars
        for (int col = 0; col < sums.length; col++) {
            double x = offX + col * scale;
            double bw = Math.max(1.0, scale);
            double bh = (sums[col] / maxV) * (VHIST_H - 16);
            gc.setFill(Color.rgb(100, 160, 255, 0.7));
            gc.fillRect(x, VHIST_H - 16 - bh, bw, bh);
        }

        // 25% threshold line
        double vThreshY = VHIST_H - 16 - 0.25 * (VHIST_H - 16);
        gc.setStroke(Color.rgb(255, 180, 0, 0.5));
        gc.setLineWidth(0.8);
        gc.strokeLine(offX, vThreshY, offX + rendW, vThreshY);

        if (grid != null) {
            // raw valleys — cyan ticks at bottom
            gc.setStroke(Color.rgb(0, 220, 220, 0.8));
            gc.setLineWidth(1.0);
            for (int v : grid.vValleys()) {
                double x = offX + v * scale;
                gc.strokeLine(x, VHIST_H - 15, x, VHIST_H - 8);
            }
            // filtered valleys — yellow full-height lines
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.5);
            for (int v : grid.vValleysFiltered()) {
                double x = offX + v * scale;
                gc.strokeLine(x, 0, x, VHIST_H - 16);
            }
            // period label
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            gc.setFill(Color.rgb(255, 220, 0, 0.9));
            gc.fillText(String.format("charW=%.2f", grid.bestCharW()), offX + 2, VHIST_H - 2);
        }
    }

    // -------------------------------------------------------------------------
    // H-histogram (left strip): raw + valley marks
    // -------------------------------------------------------------------------

    private void redrawHHistogram(TextLineExtractionResult result) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(26, 26, 26));
        gc.fillRect(0, 0, HIST_W, PREVIEW_H);

        float[] raw = result.rowSums();
        int fh = result.frameHeight(), fw = result.frameWidth();
        if (raw == null || raw.length == 0 || fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendH = fh * scale;
        double offY = (PREVIEW_H - rendH) / 2.0;

        float maxVal = 1f;
        for (float v : raw) if (v > maxVal) maxVal = v;

        double barW = HIST_W - 18;

        // signal bars
        for (int r = 0; r < raw.length; r++) {
            double y = offY + r * scale;
            double bh = Math.max(1.0, scale);
            gc.setFill(Color.rgb(80, 80, 80, 0.6));
            gc.fillRect(0, y, (raw[r] / maxVal) * barW, bh);
        }

        // 25% threshold vertical line
        double threshX = 0.25 * barW;
        gc.setStroke(Color.rgb(255, 180, 0, 0.5));
        gc.setLineWidth(0.8);
        gc.strokeLine(threshX, offY, threshX, offY + rendH);

        if (lastGrid != null) {
            // raw valleys — cyan ticks on right edge
            gc.setStroke(Color.rgb(0, 220, 220, 0.8));
            gc.setLineWidth(1.0);
            for (int v : lastGrid.hValleys()) {
                double y = offY + v * scale;
                gc.strokeLine(HIST_W - 8, y, HIST_W, y);
            }
            // filtered valleys — yellow ticks (slightly wider)
            gc.setStroke(Color.rgb(255, 220, 0, 0.95));
            gc.setLineWidth(2.0);
            for (int v : lastGrid.hValleysFiltered()) {
                double y = offY + v * scale;
                gc.strokeLine(HIST_W - 16, y, HIST_W, y);
            }
            // period label
            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
            gc.setFill(Color.rgb(255, 220, 0, 0.9));
            gc.fillText(String.format("H=%.2f", lastGrid.bestLineH()), 1, offY > 12 ? offY - 2 : 10);
        }

        gc.setFont(Font.font("System", 9));
        gc.setFill(Color.rgb(180, 180, 180, 0.5));
        gc.fillText("raw", 2, offY > 22 ? offY - 12 : 22);
    }

    // -------------------------------------------------------------------------
    // overlay: line bands + H-valley lines + V-valley filtered lines
    // -------------------------------------------------------------------------

    private void redrawOverlay(TextLineExtractionResult result) {
        GraphicsContext gc = overlay.getGraphicsContext2D();
        gc.clearRect(0, 0, PREVIEW_W, PREVIEW_H);

        int fh = result.frameHeight(), fw = result.frameWidth();
        if (fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale, rendH = fh * scale;
        double offX = (PREVIEW_W - rendW) / 2.0;
        double offY = (PREVIEW_H - rendH) / 2.0;

        // The single set of horizontal lines: the periodic grid  y(n) = y0 + n × lineH.
        // (These are the effective values — equal to the forced ones when forcing is on.)
        if (lastGrid != null && lastGrid.bestLineH() > 0) {
            drawHGrid(gc, lastGrid.bestLineY0(), lastGrid.bestLineH(), fh, scale, offX, offY, rendW,
                    Color.rgb(255, 220, 0, 0.8), "y0");
        }

        // Optionally, when the line grid is forced, also overlay the grid the
        // detector would have computed automatically (cyan), for comparison.
        boolean forcing = gridSettings != null
                && (gridSettings.forceLineH.get() || gridSettings.forceLineY0.get());
        if (showComputedLinesCb.isSelected() && forcing && lastGrid != null) {
            double computedLineH = gridSettings.forceLineH.get()
                    ? computedPeriod(lastGrid.hValleys()) : lastGrid.bestLineH();
            double computedY0 = gridSettings.forceLineY0.get() && computedLineH > 0
                    ? computedOffset(lastGrid.hValleys(), computedLineH) : lastGrid.bestLineY0();
            if (computedLineH > 0) {
                drawHGrid(gc, computedY0, computedLineH, fh, scale, offX, offY, rendW,
                        Color.rgb(0, 220, 220, 0.8), "computed");
            }
        }

        // periodic V-grid lines: x0 + N × charW — purple
        if (lastGrid != null && lastGrid.bestCharW() > 0) {
            gc.setStroke(Color.rgb(180, 100, 255, 0.7));
            gc.setLineWidth(1.0);
            double charW = lastGrid.bestCharW();
            double x0 = lastGrid.bestCharX0();
            for (double col = x0; col < fw; col += charW) {
                double x = offX + col * scale;
                gc.strokeLine(x, offY, x, offY + rendH);
            }
        }
    }

    /**
     * Draws one periodic horizontal grid: a line at every {@code y0 + n × lineH}
     * within the frame height, in {@code color}, with a small label near the
     * first line.
     */
    private static void drawHGrid(GraphicsContext gc, double y0, double lineH, int fh,
                                  double scale, double offX, double offY, double rendW,
                                  Color color, String label) {
        gc.setStroke(color);
        gc.setLineWidth(1.0);
        // start at the lowest n ≥ 0 line that is still inside the frame
        double start = y0;
        while (start < 0) start += lineH;
        boolean labelled = false;
        for (double row = start; row < fh; row += lineH) {
            double y = offY + row * scale;
            gc.strokeLine(offX, y, offX + rendW, y);
            if (!labelled) {
                gc.setFont(Font.font("Monospace", FontWeight.BOLD, 9));
                gc.setFill(color);
                gc.fillText(String.format("%s=%.1f h=%.2f", label, y0, lineH), offX + 3, y - 2);
                labelled = true;
            }
        }
    }

    /**
     * Period the detector would compute from a set of valley positions, using
     * the same span/(count−1) estimate as the detector's {@code spanPeriod}.
     * Returns 0 when fewer than two valleys are available.
     */
    private static double computedPeriod(int[] valleys) {
        if (valleys == null || valleys.length < 2) return 0;
        return (double) (valleys[valleys.length - 1] - valleys[0]) / (valleys.length - 1);
    }

    /**
     * Offset (gap phase) the detector would compute from valley positions: the
     * median of {@code valley mod period}, matching the detector's
     * {@code medianOffset}.
     */
    private static double computedOffset(int[] valleys, double period) {
        if (valleys == null || valleys.length == 0 || period <= 0) return 0;
        double[] phases = new double[valleys.length];
        for (int i = 0; i < valleys.length; i++) phases[i] = valleys[i] % period;
        java.util.Arrays.sort(phases);
        return phases[phases.length / 2];
    }

    // -------------------------------------------------------------------------
    // line strip list
    // -------------------------------------------------------------------------

    private static final class LineEntry {
        final ImageView iv = new ImageView();
        final Label lbl = new Label();
        final BorderPane pane = new BorderPane();

        LineEntry() {
            iv.setPreserveRatio(true);
            iv.setFitWidth(440);
            iv.setSmooth(false);
            lbl.setStyle("-fx-text-fill: #888; -fx-font-family: monospace; -fx-font-size: 11;");
            pane.setTop(lbl);
            pane.setCenter(iv);
            pane.setStyle("-fx-border-color: #383838; -fx-border-width: 0 0 1 0;");
            pane.setPadding(new Insets(2, 0, 4, 0));
        }

        void update(int idx, TextLine line) {
            iv.setImage(line.lineImage());
            lbl.setText(String.format("L%02d  y=%d–%d  h=%dpx",
                    idx, line.rowStart(), line.rowEnd(), line.height()));
        }
    }

    private final java.util.ArrayList<LineEntry> lineEntryPool = new java.util.ArrayList<>();

    private void rebuildLineList(TextLineExtractionResult result) {
        List<TextLine> lines = result.lines();
        int newCount = lines.size();
        int oldCount = lineList.getChildren().size();
        while (lineEntryPool.size() < newCount) lineEntryPool.add(new LineEntry());
        int updateCount = Math.min(newCount, oldCount);
        for (int i = 0; i < updateCount; i++)
            lineEntryPool.get(i).update(i, lines.get(i));
        if (newCount > oldCount) {
            var toAdd = new java.util.ArrayList<javafx.scene.Node>(newCount - oldCount);
            for (int i = oldCount; i < newCount; i++) {
                lineEntryPool.get(i).update(i, lines.get(i));
                toAdd.add(lineEntryPool.get(i).pane);
            }
            lineList.getChildren().addAll(toAdd);
        } else if (newCount < oldCount) {
            lineList.getChildren().remove(newCount, oldCount);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Spinner<Double> dblSpinner(double min, double max, double initial) {
        Spinner<Double> s = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, 0.1));
        s.setEditable(true);
        s.setPrefWidth(90);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        ((SpinnerValueFactory.DoubleSpinnerValueFactory) s.getValueFactory())
                .setConverter(new javafx.util.StringConverter<Double>() {
                    @Override
                    public String toString(Double v) {
                        return v == null ? "" : String.format(java.util.Locale.ROOT, "%.2f", v);
                    }

                    @Override
                    public Double fromString(String s) {
                        if (s == null || s.isBlank()) return 0.0;
                        return Double.parseDouble(s.trim().replace(',', '.'));
                    }
                });
        return s;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int initial) {
        Spinner<Integer> s = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial));
        s.setEditable(true);
        s.setPrefWidth(80);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
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
            if (Math.abs(n.doubleValue() - s.getValue()) > 0.001) s.getValueFactory().setValue(n.doubleValue());
        });
    }

    private VBox buildPanel(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 11));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555; -fx-border-width: 1;");
        frame.setPadding(new Insets(3));
        VBox panel = new VBox(3, label, frame);
        panel.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(frame, javafx.scene.layout.Priority.ALWAYS);
        return panel;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 13));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }

    private static Label statLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return l;
    }

    private static String valleySummary(int[] v) {
        int n = v.length;
        if (n < 2) return "";
        StringBuilder sb = new StringBuilder("  [");
        sb.append(v[0]);
        if (n >= 2) sb.append(", ").append(v[1]);
        if (n > 4) sb.append(", …");
        if (n >= 4) sb.append(", ").append(v[n - 2]);
        if (n >= 3) sb.append(", ").append(v[n - 1]);
        sb.append("]");
        double span = (double) (v[n - 1] - v[0]) / (n - 1);
        sb.append(String.format("  span/(n-1)=%.2f", span));
        return sb.toString();
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static CheckBox styledCheckBox(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setStyle("-fx-text-fill: #cccccc;");
        return cb;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(6, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

    private static RadioButton styledRadio(String text, ToggleGroup group) {
        RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(group);
        rb.setStyle("-fx-text-fill: #cccccc;");
        return rb;
    }
}
