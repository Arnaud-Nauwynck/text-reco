package fr.an.textreco.ui.tab;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.GridDetectorProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.ui.ProcessingPipeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
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

public class LineAreasDetectionView {

    private static final double PREVIEW_W = 480;
    private static final double PREVIEW_H = 360;
    private static final double HIST_W    = 100;
    private static final double VHIST_H   = 60;
    private static final double ACC_W     = 300;
    private static final double ACC_H     = 100;

    @Getter
    private final HBox root = new HBox(8);

    // --- left panel: image + histograms ---
    private final ImageView warpedView  = new ImageView();
    private final Canvas    overlay     = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Canvas    histCanvas  = new Canvas(HIST_W, PREVIEW_H);
    private final Canvas    vHistCanvas = new Canvas(PREVIEW_W, VHIST_H);

    // --- right panel: stats + accumulators + line list ---
    private final Label     lineCountLabel = statLabel("Lines: —");
    private final Label     gridLabel      = statLabel("Grid: —");

    // 4 accumulator bar charts
    private final Canvas accLineHCanvas = new Canvas(ACC_W, ACC_H);
    private final Canvas accY0Canvas    = new Canvas(ACC_W, ACC_H);
    private final Canvas accCharWCanvas = new Canvas(ACC_W, ACC_H);
    private final Canvas accX0Canvas    = new Canvas(ACC_W, ACC_H);

    private final VBox      lineList  = new VBox(3);
    private final ScrollPane lineScroll;

    // --- grid range sliders ---
    private final Slider minLineHSlider = intSlider(4,  120, 30);
    private final Slider maxLineHSlider = intSlider(4,  120, 60);
    private final Slider minCharWSlider = intSlider(2,  80,  15);
    private final Slider maxCharWSlider = intSlider(2,  80,  40);
    private final Label  minLineHLbl    = monoLabel("30");
    private final Label  maxLineHLbl    = monoLabel("60");
    private final Label  minCharWLbl    = monoLabel("15");
    private final Label  maxCharWLbl    = monoLabel("40");

    private GridDetectionResult  lastGrid    = null;
    private PreProcessingResult  lastPreProc = null;

    public LineAreasDetectionView(ProcessingPipeline pipeline, TextLineExtractorProcessor extractor) {
        pipeline.getPerspectiveImageProperty().addListener((obs, o, img) -> { if (img != null) setWarpedImage(img); });
        pipeline.getPreProcessingProperty()   .addListener((obs, o, r)   -> onPreProcessing(r));
        pipeline.getTextLinesProperty()       .addListener((obs, o, r)   -> { if (r != null) onResult(r); });
        pipeline.getGridDetectionProperty()   .addListener((obs, o, r)   -> { lastGrid = r; onGrid(r); });

        GridDetectorProcessor proc = pipeline.getGridDetector();
        bindIntSlider(minLineHSlider, minLineHLbl, proc.minLineHProperty());
        bindIntSlider(maxLineHSlider, maxLineHLbl, proc.maxLineHProperty());
        bindIntSlider(minCharWSlider, minCharWLbl, proc.minCharWProperty());
        bindIntSlider(maxCharWSlider, maxCharWLbl, proc.maxCharWProperty());

        warpedView.setPreserveRatio(true);
        warpedView.setFitWidth(PREVIEW_W);
        warpedView.setFitHeight(PREVIEW_H);
        overlay.setMouseTransparent(true);

        StackPane previewStack = new StackPane(warpedView, overlay);
        previewStack.setAlignment(Pos.TOP_LEFT);
        previewStack.setMaxSize(PREVIEW_W, PREVIEW_H);

        histCanvas .setStyle("-fx-background-color: #1a1a1a;");
        vHistCanvas.setStyle("-fx-background-color: #1a1a1a;");

        HBox previewAndHist = new HBox(2, previewStack, histCanvas);
        previewAndHist.setAlignment(Pos.TOP_LEFT);
        VBox leftContent = new VBox(2, previewAndHist, vHistCanvas);
        VBox leftPanel = buildPanel("Warped + histograms", leftContent);

        // accumulator grid: 2×2
        HBox accRow1 = new HBox(8,
                buildPanel("Line height",  accLineHCanvas),
                buildPanel("Y offset",     accY0Canvas));
        HBox accRow2 = new HBox(8,
                buildPanel("Char width",   accCharWCanvas),
                buildPanel("X offset",     accX0Canvas));
        accRow1.setAlignment(Pos.TOP_LEFT);
        accRow2.setAlignment(Pos.TOP_LEFT);

        HBox rangeRow1 = hrow(styledLabel("Line H:"),
                minLineHSlider, minLineHLbl, styledLabel("–"), maxLineHSlider, maxLineHLbl, styledLabel("px"));
        HBox rangeRow2 = hrow(styledLabel("Char W:"),
                minCharWSlider, minCharWLbl, styledLabel("–"), maxCharWSlider, maxCharWLbl, styledLabel("px"));

        lineScroll = new ScrollPane(lineList);
        lineScroll.setFitToWidth(true);
        lineScroll.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
        lineList.setPadding(new Insets(4));

        VBox rightContent = new VBox(6,
                lineCountLabel, gridLabel,
                rangeRow1, rangeRow2,
                accRow1, accRow2,
                sectionLabel("Extracted Lines"), lineScroll);
        VBox.setVgrow(lineScroll, javafx.scene.layout.Priority.ALWAYS);
        rightContent.setStyle("-fx-background-color: #1e1e1e;");

        VBox rightPanel = buildPanel("Lines & Grid", rightContent);

        root.getChildren().addAll(leftPanel, rightPanel);
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.TOP_LEFT);
    }

    // -------------------------------------------------------------------------
    // event handlers
    // -------------------------------------------------------------------------

    private void onResult(TextLineExtractionResult result) {
        redrawOverlay(result);
        redrawHistogram(result);
        rebuildLineList(result);
        lineCountLabel.setText("Lines: " + result.lines().size()
                + "  (" + result.frameWidth() + "×" + result.frameHeight() + ")");
    }

    private void setWarpedImage(Image image) {
        warpedView.setImage(image);
    }

    private void onGrid(GridDetectionResult r) {
        if (r == null) { gridLabel.setText("Grid: —"); return; }
        gridLabel.setText(String.format(
                "x0=%d  y0=%d  charW=%d  lineH=%d",
                r.bestCharX0(), r.bestLineY0(), r.bestCharW(), r.bestLineH()));
        drawAccumulators(r);
    }

    private void onPreProcessing(PreProcessingResult r) {
        if (r == null) return;
        lastPreProc = r;
        drawVHistogram(r);
    }

    // -------------------------------------------------------------------------
    // 4 accumulator bar charts
    // -------------------------------------------------------------------------

    private void drawAccumulators(GridDetectionResult r) {
        // Period charts: contrast score (max-min)/max — high = clear periodicity.
        float[] lineHScores = contrastPeriodScores(r.accY(), r.minLineH());
        drawAccBar(accLineHCanvas, lineHScores, r.bestLineH() - r.minLineH(),
                r.minLineH(), "lineH", Color.rgb(0, 200, 255, 0.9), false);

        float[] charWScores = contrastPeriodScores(r.accX(), r.minCharW());
        drawAccBar(accCharWCanvas, charWScores, r.bestCharW() - r.minCharW(),
                r.minCharW(), "charW", Color.rgb(255, 200, 0, 0.9), false);

        // Offset charts: raw accumulated votes at the detected best period.
        // The MINIMUM bin is the inter-line/inter-char gap — marked with the red line.
        int bestYTi = r.bestLineH() - r.minLineH();
        float[] y0Scores = accOffsetSlice(r.accY(), bestYTi, r.bestLineH());
        drawAccBar(accY0Canvas, y0Scores, r.bestLineY0(),
                0, "y0", Color.rgb(0, 200, 255, 0.9), true);

        int bestXTi = r.bestCharW() - r.minCharW();
        float[] x0Scores = accOffsetSlice(r.accX(), bestXTi, r.bestCharW());
        drawAccBar(accX0Canvas, x0Scores, r.bestCharX0(),
                0, "x0", Color.rgb(255, 200, 0, 0.9), true);
    }

    /**
     * For each period T, compute contrast = (max - min) / max across offset bins.
     * This is high when votes concentrate strongly at one phase (clear periodicity)
     * and low when all bins are equal (no periodicity). Same scoring as the detector.
     */
    private static float[] contrastPeriodScores(float[][] acc, int minT) {
        int numT = acc.length;
        float[] out = new float[numT];
        for (int ti = 0; ti < numT; ti++) {
            int T = ti + minT;
            float maxV = 0, minV = Float.MAX_VALUE;
            for (int o = 0; o < T && o < acc[ti].length; o++) {
                if (acc[ti][o] > maxV) maxV = acc[ti][o];
                if (acc[ti][o] < minV) minV = acc[ti][o];
            }
            out[ti] = maxV > 0 ? (maxV - minV) / maxV : 0;
        }
        return out;
    }

    /** Returns the accumulated votes for all offsets at a given period index, trimmed to [0, period). */
    private static float[] accOffsetSlice(float[][] acc, int ti, int period) {
        if (ti < 0 || ti >= acc.length) return new float[0];
        float[] slice = new float[period];
        for (int o = 0; o < period && o < acc[ti].length; o++) slice[o] = acc[ti][o];
        return slice;
    }

    /**
     * Draws a 1-D bar chart on the given canvas.
     * @param scores    bar heights (one per bin)
     * @param markedIdx index of the detected bin (red line)
     * @param minLabel  value of bin 0 (for label)
     * @param axisName  label shown at marker
     * @param barColor  fill colour for bars
     * @param markMin   true = marker is at the minimum (offset charts);
     *                  false = marker is at the maximum (period charts)
     */
    private static void drawAccBar(Canvas canvas, float[] scores, int markedIdx,
                                    int minLabel, String axisName, Color barColor,
                                    boolean markMin) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        gc.setFill(Color.rgb(15, 15, 20));
        gc.fillRect(0, 0, cw, ch);
        if (scores == null || scores.length == 0) return;

        float maxV = 1f;
        for (float v : scores) if (v > maxV) maxV = v;

        int n = scores.length;
        double bw = cw / n;

        for (int i = 0; i < n; i++) {
            double bh = (scores[i] / maxV) * (ch - 2);
            gc.setFill(i == markedIdx ? Color.rgb(255, 80, 80, 0.85) : barColor);
            gc.fillRect(i * bw, ch - bh, Math.max(1, bw - 0.5), bh);
        }

        // marker line — red vertical
        if (markedIdx >= 0 && markedIdx < n) {
            double px = markedIdx * bw + bw * 0.5;
            gc.setStroke(Color.rgb(255, 60, 60, 0.95));
            gc.setLineWidth(1.5);
            gc.strokeLine(px, 0, px, ch);

            gc.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
            gc.setFill(Color.rgb(255, 220, 220, 0.9));
            String lbl = axisName + "=" + (minLabel + markedIdx) + (markMin ? " (min)" : "");
            double tx = Math.min(px + 3, cw - lbl.length() * 6.5);
            gc.fillText(lbl, tx, 12);
        }
    }

    // -------------------------------------------------------------------------
    // V-histogram: vertical projection with valley markers
    // -------------------------------------------------------------------------

    private void drawVHistogram(PreProcessingResult r) {
        GraphicsContext gc = vHistCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, PREVIEW_W, VHIST_H);

        float[] sums = r.vColSums();
        int fw = r.frameWidth(), fh = r.frameHeight();
        if (sums == null || sums.length == 0 || fw == 0 || fh == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale;
        double offX  = (PREVIEW_W - rendW) / 2.0;

        float maxV = 1f;
        for (float v : sums) if (v > maxV) maxV = v;

        for (int col = 0; col < sums.length; col++) {
            double x  = offX + col * scale;
            double bw = Math.max(1.0, scale);
            double bh = (sums[col] / maxV) * (VHIST_H - 2);
            gc.setFill(Color.rgb(100, 160, 255, 0.85));
            gc.fillRect(x, VHIST_H - bh, bw, bh);
        }

        // valley threshold line — amber horizontal
        double vThreshY = VHIST_H - (0.25 * (VHIST_H - 2));
        gc.setStroke(Color.rgb(255, 180, 0, 0.7));
        gc.setLineWidth(0.8);
        gc.strokeLine(offX, vThreshY, offX + rendW, vThreshY);

        // valley tick marks
        int[] valleys = r.vValleys();
        if (valleys != null) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.95));
            gc.setLineWidth(1.5);
            for (int v : valleys) {
                double x = offX + v * scale;
                gc.strokeLine(x, 0, x, VHIST_H);
            }
        }

        gc.setStroke(Color.rgb(180, 180, 180, 0.4));
        gc.setLineWidth(0.5);
        gc.strokeLine(offX, VHIST_H - 1, offX + rendW, VHIST_H - 1);
    }

    // -------------------------------------------------------------------------
    // overlay: line bands + valley lines + V-valley lines
    // -------------------------------------------------------------------------

    private void redrawOverlay(TextLineExtractionResult result) {
        GraphicsContext gc = overlay.getGraphicsContext2D();
        gc.clearRect(0, 0, PREVIEW_W, PREVIEW_H);

        int fh = result.frameHeight(), fw = result.frameWidth();
        if (fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale, rendH = fh * scale;
        double offX  = (PREVIEW_W - rendW) / 2.0;
        double offY  = (PREVIEW_H - rendH) / 2.0;

        // H-valley separator lines
        gc.setStroke(Color.rgb(255, 220, 0, 0.6));
        gc.setLineWidth(1.0);
        for (int v : result.valleys()) {
            double y = offY + v * scale;
            gc.strokeLine(offX, y, offX + rendW, y);
        }

        // text-line bands
        Color[] bandFill = { Color.rgb(0, 180, 255, 0.13), Color.rgb(255, 180, 0, 0.13) };
        Color   border   = Color.rgb(0, 255, 120, 0.9);
        Color   labelCol = Color.rgb(220, 255, 220, 0.95);
        gc.setFont(Font.font("Monospace", FontWeight.BOLD, 10));
        gc.setLineWidth(1.0);

        int idx = 0;
        for (TextLine line : result.lines()) {
            double y1 = offY + line.rowStart() * scale;
            double y2 = offY + line.rowEnd()   * scale;
            double bh = y2 - y1;

            gc.setFill(bandFill[idx % 2]);
            gc.fillRect(offX, y1, rendW, bh);

            gc.setStroke(border);
            gc.strokeLine(offX, y1, offX + rendW, y1);
            gc.strokeLine(offX, y2, offX + rendW, y2);

            gc.setFill(labelCol);
            double midY = y1 + bh * 0.75;
            gc.fillText(String.format("L%02d", idx), offX + 3, midY);
            gc.fillText(String.format("y%d–%d", line.rowStart(), line.rowEnd()),
                    offX + rendW - 62, midY);
            idx++;
        }

        // V-valley separator lines
        if (lastPreProc != null && lastPreProc.vValleys() != null) {
            gc.setStroke(Color.rgb(180, 100, 255, 0.65));
            gc.setLineWidth(1.0);
            for (int v : lastPreProc.vValleys()) {
                double x = offX + v * scale;
                gc.strokeLine(x, offY, x, offY + rendH);
            }
        }
    }

    // -------------------------------------------------------------------------
    // H-histogram (left strip): raw + smoothed + threshold ticks
    // -------------------------------------------------------------------------

    private void redrawHistogram(TextLineExtractionResult result) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(26, 26, 26));
        gc.fillRect(0, 0, HIST_W, PREVIEW_H);

        float[] raw = result.rowSums();
        int     fh  = result.frameHeight(), fw = result.frameWidth();
        if (raw == null || raw.length == 0 || fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendH = fh * scale;
        double offY  = (PREVIEW_H - rendH) / 2.0;

        float maxVal = 1f;
        for (float v : raw) if (v > maxVal) maxVal = v;

        double barW = HIST_W - 2;

        for (int r = 0; r < raw.length; r++) {
            double y  = offY + r * scale;
            double bh = Math.max(1.0, scale);
            gc.setFill(Color.rgb(80, 80, 80, 0.6));
            gc.fillRect(0, y, (raw[r] / maxVal) * barW, bh);
        }

        // tick marks at each grid line top
        gc.setStroke(Color.rgb(255, 220, 0, 0.9));
        gc.setLineWidth(1.5);
        for (int v : result.valleys()) {
            if (v == 0 || v == fh) continue;
            double y = offY + v * scale;
            gc.strokeLine(HIST_W - 8, y, HIST_W, y);
        }

        gc.setFont(Font.font("System", 9));
        gc.setFill(Color.rgb(180, 180, 180, 0.7));
        gc.fillText("raw", 2, offY > 10 ? offY - 2 : 10);
    }

    // -------------------------------------------------------------------------
    // line strip list — node reuse to avoid scene-graph churn
    // -------------------------------------------------------------------------

    private static final class LineEntry {
        final ImageView  iv   = new ImageView();
        final Label      lbl  = new Label();
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
        List<TextLine> lines    = result.lines();
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

    private static void bindIntSlider(Slider slider, Label label,
                                       javafx.beans.property.IntegerProperty prop) {
        slider.valueProperty().addListener((obs, o, n) -> prop.set(n.intValue()));
        prop.addListener((obs, o, n) -> slider.setValue(n.intValue()));
        slider.setValue(prop.get());
        label.textProperty().bind(prop.asString());
    }

    private static Slider intSlider(int min, int max, int value) {
        Slider s = new Slider(min, max, value);
        s.setShowTickLabels(true);
        s.setMajorTickUnit((max - min) / 4.0);
        s.setSnapToTicks(false);
        s.setPrefWidth(160);
        return s;
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

    private static Label monoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
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
}
