package fr.an.textreco.ui.tab;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.processing.GridDetectorProcessor;
import fr.an.textreco.ui.ProcessingPipeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
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

/**
 * Displays the Hough-period accumulator heatmaps for line-height and char-width
 * detection, and overlays the detected grid on the warped image.
 *
 * Y accumulator: rows = candidate lineHeight values, cols = phase offset.
 * X accumulator: rows = candidate charWidth  values, cols = phase offset.
 * The peak cell in each accumulator is highlighted.
 */
public class GridDetectionView {

    private static final double PREVIEW_W  = 480;
    private static final double PREVIEW_H  = 360;
    private static final double ACC_W      = 420;
    private static final double ACC_H      = 160;

    @Getter
    private final VBox root = new VBox(8);

    private final ImageView warpedView  = new ImageView();
    private final Canvas    gridOverlay = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Canvas    accYCanvas  = new Canvas(ACC_W, ACC_H);
    private final Canvas    accXCanvas  = new Canvas(ACC_W, ACC_H);
    private final Label     infoLabel;

    // range sliders wired to GridDetectorProcessor properties
    private final Slider minLineHSlider = intSlider(4,  120, 8);
    private final Slider maxLineHSlider = intSlider(4,  120, 60);
    private final Slider minCharWSlider = intSlider(2,  80,  4);
    private final Slider maxCharWSlider = intSlider(2,  80,  40);

    private final Label minLineHLbl = monoLabel("8");
    private final Label maxLineHLbl = monoLabel("60");
    private final Label minCharWLbl = monoLabel("4");
    private final Label maxCharWLbl = monoLabel("40");

    public GridDetectionView(ProcessingPipeline pipeline) {
        GridDetectorProcessor proc = pipeline.getGridDetector();

        pipeline.getGridDetectionProperty() .addListener((obs, o, r) -> { if (r != null) onResult(r); });
        pipeline.getPerspectiveImageProperty().addListener((obs, o, img) -> setWarpedImage(img));

        infoLabel = monoLabel("—");

        warpedView.setPreserveRatio(true);
        warpedView.setFitWidth(PREVIEW_W);
        warpedView.setFitHeight(PREVIEW_H);
        gridOverlay.setMouseTransparent(true);

        // bind sliders ↔ processor properties
        bindIntSlider(minLineHSlider, minLineHLbl, proc.minLineHProperty());
        bindIntSlider(maxLineHSlider, maxLineHLbl, proc.maxLineHProperty());
        bindIntSlider(minCharWSlider, minCharWLbl, proc.minCharWProperty());
        bindIntSlider(maxCharWSlider, maxCharWLbl, proc.maxCharWProperty());

        HBox lineRangeRow = hrow(
                styledLabel("Line H range:"),
                minLineHSlider, minLineHLbl,
                styledLabel("–"),
                maxLineHSlider, maxLineHLbl, styledLabel("px"));
        HBox charRangeRow = hrow(
                styledLabel("Char W range:"),
                minCharWSlider, minCharWLbl,
                styledLabel("–"),
                maxCharWSlider, maxCharWLbl, styledLabel("px"));

        StackPane previewStack = new StackPane(warpedView, gridOverlay);
        previewStack.setAlignment(Pos.TOP_LEFT);
        previewStack.setMaxSize(PREVIEW_W, PREVIEW_H);

        HBox accRow = new HBox(8,
                panel("Y accumulator  (lineHeight × phase)", accYCanvas),
                panel("X accumulator  (charWidth × phase)",  accXCanvas));
        accRow.setAlignment(Pos.TOP_LEFT);

        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.getChildren().addAll(
                lineRangeRow, charRangeRow,
                infoLabel,
                accRow,
                panel("Warped + detected grid", previewStack));
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    private void setWarpedImage(Image img) {
        warpedView.setImage(img);
    }

    private void onResult(GridDetectionResult r) {
        drawAccumulator(accYCanvas, r.accY(), r.minLineH(), r.maxLineH(),
                r.bestLineH() - r.minLineH(), r.bestLineY0(), "lineH", "y0");
        drawAccumulator(accXCanvas, r.accX(), r.minCharW(), r.maxCharW(),
                r.bestCharW() - r.minCharW(), r.bestCharX0(), "charW", "x0");
        drawGrid(r);
        infoLabel.setText(String.format(
                "Line grid:  lineH=%d px   y0=%d px  |  Char grid:  charW=%d px   x0=%d px",
                r.bestLineH(), r.bestLineY0(), r.bestCharW(), r.bestCharX0()));
    }

    // -------------------------------------------------------------------------
    // accumulator heatmap
    // -------------------------------------------------------------------------

    private void drawAccumulator(Canvas canvas, float[][] acc,
                                  int minT, int maxT,
                                  int bestTi, int bestOff,
                                  String rowLabel, String colLabel) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth();
        double ch = canvas.getHeight();
        gc.setFill(Color.rgb(15, 15, 15));
        gc.fillRect(0, 0, cw, ch);
        if (acc == null || acc.length == 0) return;

        int numT   = acc.length;          // number of candidate periods
        int maxOff = maxT;                // max possible offset (for period = maxT, offset ∈ [0,maxT))

        // find global max for normalisation
        float globalMax = 1f;
        for (int ti = 0; ti < numT; ti++) {
            int T = ti + minT;
            for (int o = 0; o < T; o++) if (acc[ti][o] > globalMax) globalMax = acc[ti][o];
        }

        double cellW = cw / maxOff;
        double cellH = ch / numT;

        for (int ti = 0; ti < numT; ti++) {
            int T = ti + minT;
            double y = ti * cellH;
            for (int o = 0; o < T; o++) {
                double norm = acc[ti][o] / globalMax;
                // heat: black → blue → cyan → yellow → white
                gc.setFill(heatColor(norm));
                gc.fillRect(o * cellW, y, Math.max(1, cellW), Math.max(1, cellH));
            }
        }

        // peak crosshair
        double peakX = bestOff * cellW + cellW * 0.5;
        double peakY = bestTi  * cellH + cellH * 0.5;
        gc.setStroke(Color.rgb(255, 60, 60, 0.9));
        gc.setLineWidth(1.5);
        gc.strokeLine(peakX, 0, peakX, ch);
        gc.strokeLine(0, peakY, cw, peakY);

        // axis labels
        gc.setFont(Font.font("Monospace", 10));
        gc.setFill(Color.rgb(200, 200, 200, 0.85));
        gc.fillText(rowLabel + "=" + (bestTi + minT), 3, peakY - 3);
        gc.fillText(colLabel + "=" + bestOff, peakX + 3, ch - 3);
    }

    /** Maps [0,1] → heat colour: black→dark-blue→cyan→yellow→white. */
    private static Color heatColor(double t) {
        if (t <= 0) return Color.rgb(10, 10, 20);
        if (t < 0.25) { double s = t / 0.25; return Color.rgb((int)(s*0),   (int)(s*30),  (int)(s*180)); }
        if (t < 0.5)  { double s = (t - 0.25) / 0.25; return Color.rgb((int)(s*0),   (int)(30+s*200), (int)(180+s*50)); }
        if (t < 0.75) { double s = (t - 0.5)  / 0.25; return Color.rgb((int)(s*255), (int)(230+s*25), (int)(230-s*180)); }
        { double s = (t - 0.75) / 0.25; return Color.rgb(255, (int)(255), (int)(50+s*200)); }
    }

    // -------------------------------------------------------------------------
    // grid overlay on warped image
    // -------------------------------------------------------------------------

    private void drawGrid(GridDetectionResult r) {
        GraphicsContext gc = gridOverlay.getGraphicsContext2D();
        gc.clearRect(0, 0, PREVIEW_W, PREVIEW_H);

        int fw = r.frameWidth(), fh = r.frameHeight();
        if (fw == 0 || fh == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale, rendH = fh * scale;
        double offX  = (PREVIEW_W - rendW) / 2.0;
        double offY  = (PREVIEW_H - rendH) / 2.0;

        // horizontal grid lines (line separators)
        gc.setStroke(Color.rgb(0, 220, 255, 0.55));
        gc.setLineWidth(0.8);
        int lineH = r.bestLineH(), y0 = r.bestLineY0();
        for (int y = y0; y < fh; y += lineH) {
            double dy = offY + y * scale;
            gc.strokeLine(offX, dy, offX + rendW, dy);
        }
        // extend backwards to top edge
        for (int y = y0 - lineH; y >= 0; y -= lineH) {
            double dy = offY + y * scale;
            gc.strokeLine(offX, dy, offX + rendW, dy);
        }

        // vertical grid lines (char separators)
        gc.setStroke(Color.rgb(255, 200, 0, 0.45));
        gc.setLineWidth(0.8);
        int charW = r.bestCharW(), x0 = r.bestCharX0();
        for (int x = x0; x < fw; x += charW) {
            double dx = offX + x * scale;
            gc.strokeLine(dx, offY, dx, offY + rendH);
        }
        for (int x = x0 - charW; x >= 0; x -= charW) {
            double dx = offX + x * scale;
            gc.strokeLine(dx, offY, dx, offY + rendH);
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
        s.setPrefWidth(200);
        return s;
    }

    private VBox panel(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        lbl.setStyle("-fx-text-fill: #bbbbbb;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        frame.setPadding(new Insets(3));
        VBox p = new VBox(3, lbl, frame);
        p.setAlignment(Pos.TOP_LEFT);
        return p;
    }

    private static Label styledLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static Label monoLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return l;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }
}
