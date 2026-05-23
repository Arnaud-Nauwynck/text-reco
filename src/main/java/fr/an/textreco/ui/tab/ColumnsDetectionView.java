package fr.an.textreco.ui.tab;

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
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

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
    private int[]                    colStarts    = new int[0]; // detected column start pixels
    private int                      charWidth    = 0;          // median column width

    public ColumnsDetectionView(ProcessingPipeline pipeline) {
        pipeline.getTextLinesProperty().addListener((obs, o, r) -> { if (r != null) onResult(r); });
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
            recomputeColumns();
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
        recomputeColumns();
    }

    // -------------------------------------------------------------------------
    // column detection from line image
    // -------------------------------------------------------------------------

    private void recomputeColumns() {
        colStarts = new int[0];
        charWidth = 0;

        if (lastResult == null || lastResult.lines().isEmpty()) {
            colSlider.setMax(0);
            colSlider.setValue(0);
            refreshDisplay();
            return;
        }

        int lineIdx = clampedLineIdx();
        TextLine line = lastResult.lines().get(lineIdx);
        WritableImage img = line.lineImage();
        if (img == null || img.getWidth() == 0 || img.getHeight() == 0) {
            refreshDisplay();
            return;
        }

        int imgW = (int) img.getWidth();
        int imgH = (int) img.getHeight();

        // --- vertical projection from WritableImage pixel data ---
        float[] colSums = new float[imgW];
        PixelReader pr = img.getPixelReader();
        for (int x = 0; x < imgW; x++) {
            float sum = 0f;
            for (int y = 0; y < imgH; y++) {
                int argb = pr.getArgb(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                sum += (r + g + b) / 3f;
            }
            colSums[x] = sum;
        }

        // --- smooth ---
        float[] smoothed = new float[imgW];
        boxSmooth(colSums, smoothed, imgW, 2);

        // --- detect column valleys (same algorithm as TextLineExtractorProcessor) ---
        float globalMax = 1f;
        for (float v : smoothed) if (v > globalMax) globalMax = v;

        double vThresh = 0.25 * globalMax;
        int    vHWin   = 3;
        List<Integer> valleys = new ArrayList<>();
        valleys.add(0);
        for (int x = 1; x < imgW - 1; x++) {
            if (smoothed[x] >= vThresh) continue;
            boolean isMin = true;
            int lo = Math.max(0, x - vHWin), hi = Math.min(imgW - 1, x + vHWin);
            for (int k = lo; k <= hi; k++) if (smoothed[k] < smoothed[x]) { isMin = false; break; }
            if (isMin) valleys.add(x);
        }
        valleys.add(imgW);

        // --- merge adjacent valleys (no peak between them) ---
        double peakMin = 0.05 * globalMax;
        List<Integer> merged = new ArrayList<>();
        merged.add(valleys.get(0));
        for (int i = 1; i < valleys.size() - 1; i++) {
            int prev = merged.get(merged.size() - 1);
            int cur  = valleys.get(i);
            boolean hasPeak = false;
            for (int x = prev; x <= cur; x++) if (smoothed[x] > peakMin) { hasPeak = true; break; }
            if (hasPeak) merged.add(cur);
        }
        merged.add(valleys.get(valleys.size() - 1));

        // --- build column start list and compute median char width ---
        List<Integer> starts = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < merged.size() - 1; i++) {
            int lo = merged.get(i), hi = merged.get(i + 1);
            int w = hi - lo;
            if (w < 2) continue;
            starts.add(lo);
            widths.add(w);
        }

        colStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        charWidth = widths.isEmpty() ? 0 : median(widths);

        int numCols = colStarts.length;
        colSlider.setMax(numCols == 0 ? 0 : numCols - 1);
        if (colSlider.getValue() >= numCols) colSlider.setValue(0);

        // draw histogram with valley/column markers
        drawHistogram(colSums, smoothed, merged, imgW, globalMax);

        refreshDisplay();
    }

    // -------------------------------------------------------------------------
    // histogram drawing
    // -------------------------------------------------------------------------

    private void drawHistogram(float[] raw, float[] smooth, List<Integer> valleys,
                                int imgW, float globalMax) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, HIST_W, HIST_H);
        if (imgW == 0) return;

        int offset = (int) offsetSlider.getValue();
        double scaleX = HIST_W / (double) imgW;
        double barH   = HIST_H - 4;

        // raw — dim grey
        for (int x = 0; x < raw.length; x++) {
            double px = (x + offset) * scaleX;
            double bh = (raw[x] / globalMax) * barH;
            gc.setFill(Color.rgb(70, 70, 70, 0.6));
            gc.fillRect(px, HIST_H - bh, Math.max(1, scaleX), bh);
        }

        // smoothed — green polyline
        gc.setStroke(Color.rgb(0, 220, 100, 0.95));
        gc.setLineWidth(1.5);
        gc.beginPath();
        for (int x = 0; x < smooth.length; x++) {
            double px = (x + offset) * scaleX + scaleX * 0.5;
            double py = HIST_H - (smooth[x] / globalMax) * barH;
            if (x == 0) gc.moveTo(px, py); else gc.lineTo(px, py);
        }
        gc.stroke();

        // valley lines — amber
        gc.setStroke(Color.rgb(255, 180, 0, 0.8));
        gc.setLineWidth(1.0);
        for (int v : valleys) {
            if (v == 0 || v == imgW) continue;
            double px = (v + offset) * scaleX;
            gc.strokeLine(px, 0, px, HIST_H);
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

    private static void boxSmooth(float[] src, float[] dst, int n, int radius) {
        if (radius <= 0) { System.arraycopy(src, 0, dst, 0, n); return; }
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + src[i];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - radius), hi = Math.min(n - 1, i + radius);
            dst[i] = (float) ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1));
        }
    }

    private static int median(List<Integer> vals) {
        List<Integer> sorted = new ArrayList<>(vals);
        sorted.sort(Integer::compare);
        return sorted.get(sorted.size() / 2);
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
