package fr.an.textreco.ui.tab;

import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class TextLinesTab {

    private static final double PREVIEW_W  = 480;
    private static final double PREVIEW_H  = 360;
    private static final double HIST_W     = 80;   // width of histogram strip beside preview

    @Getter
    private final HBox root = new HBox(8);

    private final ImageView warpedView     = new ImageView();
    private final Canvas    overlay        = new Canvas(PREVIEW_W, PREVIEW_H);
    private final Canvas    histCanvas     = new Canvas(HIST_W, PREVIEW_H);
    private final Label     lineCountLabel = statLabel("Lines: —");
    private final VBox      lineList       = new VBox(3);
    private final ScrollPane lineScroll;

    private final Label fillRatioValue = statLabel("0.02");
    private final Label minGapValue    = statLabel("3");
    private final Label minHeightValue = statLabel("6");
    private final Label maxHeightValue = statLabel("120");

    // kept to draw threshold line on histogram
    private final TextLineExtractorProcessor extractor;

    public TextLinesTab(TextLineExtractorProcessor extractor) {
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

        VBox leftPanel = buildPanel("Warped + histogram", previewAndHist);

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

        Label sectionTune = sectionLabel("Parameters");
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        addSliderRow(grid, 0, "Min fill ratio:", 0.001, 0.3, extractor.getMinFillRatio(), 0.05,
                fillRatioValue, v -> { extractor.setMinFillRatio(v); fillRatioValue.setText(String.format("%.3f", v)); });
        addSliderRow(grid, 1, "Min line gap (px):", 1, 20, extractor.getMinLineGap(), 5,
                minGapValue, v -> { extractor.setMinLineGap((int) v); minGapValue.setText(String.valueOf((int) v)); });
        addSliderRow(grid, 2, "Min height (px):", 2, 30, extractor.getMinLineHeight(), 5,
                minHeightValue, v -> { extractor.setMinLineHeight((int) v); minHeightValue.setText(String.valueOf((int) v)); });
        addSliderRow(grid, 3, "Max height (px):", 20, 200, extractor.getMaxLineHeight(), 20,
                maxHeightValue, v -> { extractor.setMaxLineHeight((int) v); maxHeightValue.setText(String.valueOf((int) v)); });

        Label sectionLines = sectionLabel("Lines");
        box.getChildren().addAll(sectionTune, grid, new Separator(), sectionLines, lineCountLabel, scroll);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        box.setStyle("-fx-background-color: #1e1e1e;");
        return box;
    }

    private void addSliderRow(GridPane grid, int row, String name,
                               double min, double max, double init, double tick,
                               Label valueLabel, java.util.function.DoubleConsumer onChange) {
        Slider slider = new Slider(min, max, init);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(tick * 5);
        slider.setPrefWidth(220);
        slider.valueProperty().addListener((obs, o, n) -> onChange.accept(n.doubleValue()));
        grid.add(rowLabel(name), 0, row);
        grid.add(slider,         1, row);
        grid.add(valueLabel,     2, row);
    }

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

    // -------------------------------------------------------------------------
    // overlay: alternating bands + borders + labels over the warped preview
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

        Color[] bandFill  = { Color.rgb(0, 180, 255, 0.13), Color.rgb(255, 180, 0, 0.13) };
        Color   border    = Color.rgb(0, 255, 120, 0.9);
        Color   labelCol  = Color.rgb(220, 255, 220, 0.95);

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
    // histogram: vertical bar chart of row sums, with threshold line
    // -------------------------------------------------------------------------
    private void redrawHistogram(TextLineExtractionResult result) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(26, 26, 26, 1));
        gc.fillRect(0, 0, HIST_W, PREVIEW_H);

        float[] sums = result.rowSums();
        int fh = result.frameHeight();
        int fw = result.frameWidth();
        if (sums == null || sums.length == 0 || fh == 0 || fw == 0) return;

        double scale  = Math.min(PREVIEW_W / (double) fw, PREVIEW_H / (double) fh);
        double rendH  = fh * scale;
        double offY   = (PREVIEW_H - rendH) / 2.0;

        // find max for normalisation
        float maxVal = 1f;
        for (float v : sums) if (v > maxVal) maxVal = v;

        // threshold in the same unit as rowSums (fill * w * 255)
        double threshold = extractor.getMinFillRatio() * fw * 255.0;
        double threshX   = (threshold / maxVal) * (HIST_W - 2);

        // draw bars
        for (int r = 0; r < sums.length; r++) {
            double y  = offY + r * scale;
            double bh = Math.max(1.0, scale);
            double bw = (sums[r] / maxVal) * (HIST_W - 2);

            boolean active = sums[r] >= threshold;
            gc.setFill(active ? Color.rgb(0, 220, 100, 0.85) : Color.rgb(80, 80, 80, 0.7));
            gc.fillRect(0, y, bw, bh);
        }

        // threshold vertical line
        gc.setStroke(Color.rgb(255, 80, 80, 0.9));
        gc.setLineWidth(1.0);
        gc.strokeLine(threshX, offY, threshX, offY + rendH);

        // axis label
        gc.setFill(Color.rgb(180, 180, 180, 0.8));
        gc.setFont(Font.font("System", 9));
        gc.fillText("hist", 2, offY > 10 ? offY - 2 : 10);
    }

    // -------------------------------------------------------------------------
    // line strip list
    // -------------------------------------------------------------------------
    private void rebuildLineList(TextLineExtractionResult result) {
        lineList.getChildren().clear();
        int idx = 0;
        for (TextLine line : result.lines()) {
            ImageView iv = new ImageView(line.lineImage());
            iv.setPreserveRatio(true);
            iv.setFitWidth(440);
            iv.setSmooth(false);

            Label lbl = new Label(String.format("L%02d  y=%d–%d  h=%dpx",
                    idx++, line.rowStart(), line.rowEnd(), line.height()));
            lbl.setStyle("-fx-text-fill: #888; -fx-font-family: monospace; -fx-font-size: 11;");

            BorderPane entry = new BorderPane();
            entry.setTop(lbl);
            entry.setCenter(iv);
            entry.setStyle("-fx-border-color: #383838; -fx-border-width: 0 0 1 0;");
            entry.setPadding(new Insets(2, 0, 4, 0));
            lineList.getChildren().add(entry);
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
