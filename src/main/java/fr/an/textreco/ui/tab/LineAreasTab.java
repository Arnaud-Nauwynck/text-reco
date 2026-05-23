package fr.an.textreco.ui.tab;

import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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

public class LineAreasTab {

    private static final double PREVIEW_W = 480;
    private static final double PREVIEW_H = 360;
    private static final double HIST_W    = 100;
    private static final double VHIST_H   = 60;

    @Getter
    private final HBox root = new HBox(8);

    private final ImageView warpedView     = new ImageView();
    private final Canvas    overlay        = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Canvas    histCanvas     = new Canvas(HIST_W, PREVIEW_H);
    private final Canvas    vHistCanvas    = new Canvas(PREVIEW_W, VHIST_H);
    private final Label     lineCountLabel = statLabel("Lines: —");
    private final VBox      lineList       = new VBox(3);
    private final ScrollPane lineScroll;

    private final TextLineExtractorProcessor extractor;

    public LineAreasTab(TextLineExtractorProcessor extractor) {
        this.extractor = extractor;

        warpedView.setPreserveRatio(true);
        warpedView.setFitWidth(PREVIEW_W);
        warpedView.setFitHeight(PREVIEW_H);
        overlay.setMouseTransparent(true);

        StackPane previewStack = new StackPane(warpedView, overlay);
        previewStack.setAlignment(Pos.TOP_LEFT);
        previewStack.setMaxSize(PREVIEW_W, PREVIEW_H);

        histCanvas.setStyle("-fx-background-color: #1a1a1a;");
        HBox previewAndHist = new HBox(2, previewStack, histCanvas);
        previewAndHist.setAlignment(Pos.TOP_LEFT);

        vHistCanvas.setStyle("-fx-background-color: #1a1a1a;");
        VBox leftContent = new VBox(2, previewAndHist, vHistCanvas);

        VBox leftPanel = buildPanel("Warped + histograms", leftContent);

        lineScroll = new ScrollPane(lineList);
        lineScroll.setFitToWidth(true);
        lineScroll.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
        lineList.setPadding(new Insets(4));

        VBox rightPanel = buildPanel("Extracted Lines", buildRightContent(lineScroll));

        root.getChildren().addAll(leftPanel, rightPanel);
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.TOP_LEFT);
    }

    private VBox buildRightContent(ScrollPane scroll) {
        VBox box = new VBox(8);
        box.setPrefWidth(480);
        box.getChildren().addAll(sectionLabel("Lines"), lineCountLabel, scroll);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        box.setStyle("-fx-background-color: #1e1e1e;");
        return box;
    }

    // -------------------------------------------------------------------------
    // public update entry
    // -------------------------------------------------------------------------

    public void onResult(TextLineExtractionResult result) {
        redrawOverlay(result);
        redrawHistogram(result);
        rebuildLineList(result);
        lineCountLabel.setText("Lines: " + result.lines().size()
                + "  (" + result.frameWidth() + "×" + result.frameHeight() + ")");
    }

    public void setWarpedImage(Image image) {
        warpedView.setImage(image);
    }

    public void onPreProcessing(PreProcessingResult r) {
        if (r == null) return;
        drawVHistogram(r.vColSums(), r.frameWidth(), r.frameHeight());
    }

    // -------------------------------------------------------------------------
    // V-histogram: vertical projection (col sums from morph-vert)
    // -------------------------------------------------------------------------

    private void drawVHistogram(float[] sums, int fw, int fh) {
        GraphicsContext gc = vHistCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, PREVIEW_W, VHIST_H);
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
        gc.setStroke(Color.rgb(180, 180, 180, 0.4));
        gc.setLineWidth(0.5);
        gc.strokeLine(offX, VHIST_H - 1, offX + rendW, VHIST_H - 1);
    }

    // -------------------------------------------------------------------------
    // overlay: alternating band fills + valley lines + border + labels
    // -------------------------------------------------------------------------

    private void redrawOverlay(TextLineExtractionResult result) {
        GraphicsContext gc = overlay.getGraphicsContext2D();
        gc.clearRect(0, 0, PREVIEW_W, PREVIEW_H);

        int fh = result.frameHeight();
        int fw = result.frameWidth();
        if (fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendW = fw * scale;
        double rendH = fh * scale;
        double offX  = (PREVIEW_W - rendW) / 2.0;
        double offY  = (PREVIEW_H - rendH) / 2.0;

        // draw valley separator lines
        gc.setStroke(Color.rgb(255, 220, 0, 0.6));
        gc.setLineWidth(1.0);
        for (int v : result.valleys()) {
            double y = offY + v * scale;
            gc.strokeLine(offX, y, offX + rendW, y);
        }

        // draw detected text-line bands
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
    }

    // -------------------------------------------------------------------------
    // histogram: raw (grey) + smoothed (green) + valley threshold line + valley ticks
    // -------------------------------------------------------------------------

    private void redrawHistogram(TextLineExtractionResult result) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(26, 26, 26));
        gc.fillRect(0, 0, HIST_W, PREVIEW_H);

        float[] raw      = result.rowSums();
        float[] smooth   = result.smoothedSums();
        int     fh       = result.frameHeight();
        int     fw       = result.frameWidth();
        if (raw == null || raw.length == 0 || fh == 0 || fw == 0) return;

        double scale = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendH = fh * scale;
        double offY  = (PREVIEW_H - rendH) / 2.0;

        float maxVal = 1f;
        for (float v : smooth) if (v > maxVal) maxVal = v;

        double barW = HIST_W - 2;

        // raw signal — dim grey bars
        for (int r = 0; r < raw.length; r++) {
            double y  = offY + r * scale;
            double bh = Math.max(1.0, scale);
            double bw = (raw[r] / maxVal) * barW;
            gc.setFill(Color.rgb(80, 80, 80, 0.6));
            gc.fillRect(0, y, bw, bh);
        }

        // smoothed signal — bright green polyline
        gc.setStroke(Color.rgb(0, 220, 100, 0.95));
        gc.setLineWidth(1.5);
        gc.beginPath();
        for (int r = 0; r < smooth.length; r++) {
            double x = (smooth[r] / maxVal) * barW;
            double y = offY + r * scale + scale * 0.5;
            if (r == 0) gc.moveTo(x, y); else gc.lineTo(x, y);
        }
        gc.stroke();

        // valley threshold line — amber vertical
        double vThreshX = extractor.getValleyThreshold() * barW;
        gc.setStroke(Color.rgb(255, 180, 0, 0.85));
        gc.setLineWidth(1.0);
        gc.strokeLine(vThreshX, offY, vThreshX, offY + rendH);

        // min-peak threshold line — cyan vertical
        double peakThreshX = extractor.getMinPeakRatio() * barW;
        gc.setStroke(Color.rgb(0, 200, 255, 0.7));
        gc.strokeLine(peakThreshX, offY, peakThreshX, offY + rendH);

        // valley tick marks on right edge
        gc.setStroke(Color.rgb(255, 220, 0, 0.9));
        gc.setLineWidth(1.5);
        for (int v : result.valleys()) {
            if (v == 0 || v == fh) continue;
            double y = offY + v * scale;
            gc.strokeLine(HIST_W - 8, y, HIST_W, y);
        }

        // legend
        gc.setFont(Font.font("System", 9));
        gc.setFill(Color.rgb(180, 180, 180, 0.7));
        gc.fillText("raw", 2, offY > 10 ? offY - 2 : 10);
    }

    // -------------------------------------------------------------------------
    // line strip list — nodes are reused across frames to avoid scene-graph churn
    // -------------------------------------------------------------------------

    private static final class LineEntry {
        final ImageView iv  = new ImageView();
        final Label     lbl = new Label();
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

        // grow pool if needed
        while (lineEntryPool.size() < newCount) lineEntryPool.add(new LineEntry());

        // update existing nodes in-place
        int updateCount = Math.min(newCount, oldCount);
        for (int i = 0; i < updateCount; i++) {
            lineEntryPool.get(i).update(i, lines.get(i));
        }

        if (newCount > oldCount) {
            // add extra nodes at the end — one bulk addAll to minimise list-change events
            var toAdd = new java.util.ArrayList<javafx.scene.Node>(newCount - oldCount);
            for (int i = oldCount; i < newCount; i++) {
                lineEntryPool.get(i).update(i, lines.get(i));
                toAdd.add(lineEntryPool.get(i).pane);
            }
            lineList.getChildren().addAll(toAdd);
        } else if (newCount < oldCount) {
            // remove from the end in one shot
            lineList.getChildren().remove(newCount, oldCount);
        }
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private VBox buildPanel(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555; -fx-border-width: 1;");
        frame.setPadding(new Insets(4));
        VBox panel = new VBox(4, label, frame);
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
}
