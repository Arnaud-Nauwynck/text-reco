package fr.an.textreco.ui.tab;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.ui.ProcessingPipeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class ColumnsDetectionView {

    private static final double LINE_VIEW_W = 800;
    private static final double LINE_VIEW_H = 80;
    private static final double HIST_W      = LINE_VIEW_W;
    private static final double HIST_H      = 80;
    private static final double CHAR_VIEW_W = 160;
    private static final double CHAR_VIEW_H = 160;

    @Getter
    private final VBox root = new VBox(8);

    // --- controls ---
    private final Slider lineSlider   = new Slider(0, 0, 0);
    private final Label  lineValLabel = monoLabel("—");
    private final Slider colSlider    = new Slider(0, 0, 0);
    private final Label  colValLabel  = monoLabel("—");
    private final Slider offsetSlider = new Slider(-50, 50, 0);
    private final Label  offsetValLabel = monoLabel("0");

    // --- line image + column grid overlay ---
    private final ImageView lineView    = new ImageView();
    private final Canvas    lineOverlay = new Canvas(LINE_VIEW_W, LINE_VIEW_H);

    // --- vertical histogram of selected line ---
    private final Canvas histCanvas = new Canvas(HIST_W, HIST_H);

    // --- zoomed char crop ---
    private final ImageView charView  = new ImageView();
    private final Label     infoLabel = monoLabel("");

    // --- state ---
    private TextLineExtractionResult lastResult   = null;
    private GridDetectionResult      lastGrid     = null;
    private PreProcessingResult      lastPreProc  = null;
    private int[]                    colStarts    = new int[0];
    private int                      charWidth    = 0;

    public ColumnsDetectionView(ProcessingPipeline pipeline) {
        pipeline.getTextLinesProperty()    .addListener((obs, o, r) -> { if (r != null) onResult(r); });
        pipeline.getGridDetectionProperty().addListener((obs, o, r) -> { lastGrid = r;    onGridOrPreProc(); });
        pipeline.getPreProcessingProperty().addListener((obs, o, r) -> { lastPreProc = r; onGridOrPreProc(); });
        lineView.setPreserveRatio(true);
        lineView.setFitWidth(LINE_VIEW_W);
        lineView.setFitHeight(LINE_VIEW_H);
        lineView.setSmooth(false);
        lineOverlay.setMouseTransparent(true);

        charView.setPreserveRatio(true);
        charView.setFitWidth(CHAR_VIEW_W);
        charView.setFitHeight(CHAR_VIEW_H);
        charView.setSmooth(false);

        histCanvas.setStyle("-fx-background-color: #1a1a1a;");

        // line slider
        lineSlider.setShowTickLabels(true);
        lineSlider.setMajorTickUnit(1);
        lineSlider.setSnapToTicks(true);
        lineSlider.setPrefWidth(400);
        lineSlider.valueProperty().addListener((obs, o, n) -> {
            lineValLabel.setText(String.valueOf(n.intValue()));
            refreshDisplay();
        });

        // col index slider
        colSlider.setShowTickLabels(true);
        colSlider.setMajorTickUnit(5);
        colSlider.setSnapToTicks(true);
        colSlider.setPrefWidth(400);
        colSlider.valueProperty().addListener((obs, o, n) -> {
            colValLabel.setText(String.valueOf(n.intValue()));
            refreshDisplay();
        });

        // offset slider
        offsetSlider.setShowTickLabels(true);
        offsetSlider.setMajorTickUnit(10);
        offsetSlider.setPrefWidth(400);
        offsetSlider.valueProperty().addListener((obs, o, n) -> {
            offsetValLabel.setText(String.valueOf(n.intValue()));
            refreshDisplay();
        });

        HBox lineRow   = hrow(styledLabel("Line index:"),  lineSlider,   lineValLabel);
        HBox colRow    = hrow(styledLabel("Col index:"),   colSlider,    colValLabel);
        HBox offsetRow = hrow(styledLabel("Offset (px):"), offsetSlider, offsetValLabel);

        StackPane lineStack = new StackPane(lineView, lineOverlay);
        lineStack.setAlignment(Pos.TOP_LEFT);
        lineStack.setMaxSize(LINE_VIEW_W, LINE_VIEW_H);

        VBox charPanel = panel("Char Crop", charView);
        VBox infoPanel = panel("Info", infoLabel);

        HBox bottomRow = new HBox(12, charPanel, infoPanel);
        bottomRow.setAlignment(Pos.TOP_LEFT);

        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.getChildren().addAll(
                lineRow, colRow, offsetRow,
                panel("V-Histogram (selected line)", histCanvas),
                panel("Selected Line + columns", lineStack),
                bottomRow);
    }

    // -------------------------------------------------------------------------
    // public update
    // -------------------------------------------------------------------------

    private void onResult(TextLineExtractionResult result) {
        this.lastResult = result;
        int lineCount = result == null ? 0 : result.lines().size();
        if (lineCount == 0) {
            lineSlider.setMax(0);
            lineSlider.setValue(0);
        } else {
            lineSlider.setMax(lineCount - 1);
            if (lineSlider.getValue() >= lineCount) lineSlider.setValue(0);
        }
        refreshDisplay();
    }

    // -------------------------------------------------------------------------
    // column detection from line image
    // -------------------------------------------------------------------------

    private void onGridOrPreProc() {
        if (lastGrid == null || lastPreProc == null) return;

        int charW = lastGrid.bestCharW();
        int x0    = lastGrid.bestCharX0();
        int fw    = lastPreProc.frameWidth();

        // rebuild colStarts from the detected periodic grid
        int count = fw > 0 && charW > 0 ? (fw - x0 + charW - 1) / charW : 0;
        int[] starts = new int[count];
        for (int i = 0; i < count; i++) starts[i] = x0 + i * charW;
        // include columns that extend backwards from x0 to 0
        int backCount = x0 / charW;
        if (backCount > 0) {
            int[] full = new int[backCount + count];
            for (int i = 0; i < backCount; i++) full[i] = x0 - (backCount - i) * charW;
            System.arraycopy(starts, 0, full, backCount, count);
            starts = full;
        }

        colStarts = starts;
        charWidth = charW;

        int numCols = colStarts.length;
        colSlider.setMax(numCols == 0 ? 0 : numCols - 1);
        if (colSlider.getValue() >= numCols) colSlider.setValue(0);

        drawHistogram();
        refreshDisplay();
    }

    // -------------------------------------------------------------------------
    // histogram drawing
    // -------------------------------------------------------------------------

    private void drawHistogram() {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, HIST_W, HIST_H);
        if (lastPreProc == null) return;

        float[] sums = lastPreProc.vColSums();
        int[]   valleys = lastPreProc.vValleys();
        int     fw  = lastPreProc.frameWidth();
        if (sums == null || sums.length == 0 || fw == 0) return;

        double scaleX = HIST_W / (double) fw;
        double barH   = HIST_H - 4;

        float globalMax = 1f;
        for (float v : sums) if (v > globalMax) globalMax = v;

        // bars — blue
        for (int x = 0; x < sums.length; x++) {
            double px = x * scaleX;
            double bh = (sums[x] / globalMax) * barH;
            gc.setFill(Color.rgb(100, 160, 255, 0.85));
            gc.fillRect(px, HIST_H - bh, Math.max(1, scaleX), bh);
        }

        // valley threshold line — dim amber horizontal
        double vThreshY = HIST_H - (0.25 * barH);
        gc.setStroke(Color.rgb(255, 180, 0, 0.55));
        gc.setLineWidth(0.8);
        gc.strokeLine(0, vThreshY, HIST_W, vThreshY);

        // valley markers — bright amber verticals (from PreProcessingResult)
        if (valleys != null) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.2);
            for (int v : valleys) {
                double px = v * scaleX;
                gc.strokeLine(px, 0, px, HIST_H);
            }
        }

        // grid lines from detected charWidth — purple
        if (lastGrid != null) {
            int charW = lastGrid.bestCharW();
            int x0    = lastGrid.bestCharX0();
            gc.setStroke(Color.rgb(180, 100, 255, 0.7));
            gc.setLineWidth(1.0);
            for (int x = x0; x < fw; x += charW) {
                double px = x * scaleX;
                gc.strokeLine(px, 0, px, HIST_H);
            }
            for (int x = x0 - charW; x >= 0; x -= charW) {
                double px = x * scaleX;
                gc.strokeLine(px, 0, px, HIST_H);
            }
        }
    }

    // -------------------------------------------------------------------------
    // display refresh
    // -------------------------------------------------------------------------

    private void refreshDisplay() {
        GraphicsContext gc = lineOverlay.getGraphicsContext2D();
        gc.clearRect(0, 0, LINE_VIEW_W, LINE_VIEW_H);

        if (lastResult == null || lastResult.lines().isEmpty()) {
            lineView.setImage(null);
            charView.setImage(null);
            infoLabel.setText("No lines detected.");
            return;
        }

        int lineIdx = clampedLineIdx();
        TextLine line = lastResult.lines().get(lineIdx);
        WritableImage lineImg = line.lineImage();
        lineView.setImage(lineImg);

        if (lineImg == null || lineImg.getWidth() == 0) {
            charView.setImage(null);
            infoLabel.setText("No image for line " + lineIdx);
            return;
        }

        int imgW = (int) lineImg.getWidth();
        int imgH = (int) lineImg.getHeight();

        // image → display scaling (preserveRatio fit)
        double scaleX = Math.min(LINE_VIEW_W / imgW, LINE_VIEW_H / imgH);
        double rendW  = imgW * scaleX;
        double rendH  = imgH * scaleX;
        double offX   = (LINE_VIEW_W - rendW) / 2.0;
        double offY   = (LINE_VIEW_H - rendH) / 2.0;

        int offset = (int) offsetSlider.getValue();

        // draw all column grid lines — dim
        gc.setStroke(Color.rgb(80, 160, 255, 0.35));
        gc.setLineWidth(1.0);
        for (int cs : colStarts) {
            double px = offX + (cs + offset) * scaleX;
            if (px >= offX && px <= offX + rendW)
                gc.strokeLine(px, offY, px, offY + rendH);
        }

        // highlight selected column
        int colIdx = colStarts.length == 0 ? -1 : (int) Math.round(
                Math.max(0, Math.min(colSlider.getValue(), colStarts.length - 1)));

        if (colIdx >= 0 && colIdx < colStarts.length) {
            int cxPx  = colStarts[colIdx] + offset;
            int cxEnd = (colIdx + 1 < colStarts.length)
                    ? colStarts[colIdx + 1] + offset
                    : cxPx + charWidth;

            double px1 = offX + cxPx  * scaleX;
            double px2 = offX + cxEnd * scaleX;

            // highlight band
            gc.setFill(Color.rgb(255, 220, 0, 0.18));
            gc.fillRect(px1, offY, px2 - px1, rendH);

            // left edge cursor
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.5);
            gc.strokeLine(px1, offY, px1, offY + rendH);

            // crop char from line image
            int cropX = Math.max(0, cxPx);
            int cropW = Math.max(1, Math.min(cxEnd - cxPx, imgW - cropX));
            if (cropW > 0 && imgH > 0) {
                charView.setImage(new WritableImage(lineImg.getPixelReader(), cropX, 0, cropW, imgH));
            }

            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n" +
                    "Col %d/%d  x=%d–%d  w=%dpx%n" +
                    "charWidth (median)=%dpx  offset=%dpx",
                    lineIdx, line.rowStart(), line.rowEnd(), line.height(),
                    colIdx, colStarts.length, cxPx, cxEnd, cropW,
                    charWidth, offset));
        } else {
            charView.setImage(null);
            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n%d columns detected  charWidth=%dpx",
                    lineIdx, line.rowStart(), line.rowEnd(), line.height(),
                    colStarts.length, charWidth));
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private int clampedLineIdx() {
        if (lastResult == null) return 0;
        int n = lastResult.lines().size();
        return n == 0 ? 0 : Math.max(0, Math.min((int) lineSlider.getValue(), n - 1));
    }

    private VBox panel(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        lbl.setStyle("-fx-text-fill: #bbbbbb;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        frame.setPadding(new Insets(4));
        VBox p = new VBox(3, lbl, frame);
        p.setAlignment(Pos.TOP_LEFT);
        return p;
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static Label monoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return l;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }
}
