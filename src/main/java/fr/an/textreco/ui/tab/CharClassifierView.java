package fr.an.textreco.ui.tab;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.CharTemplateClassifier;
import fr.an.textreco.processing.CharTemplateDb;
import fr.an.textreco.processing.PreComputedFeaturesChar;
import fr.an.textreco.util.FxImageUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import org.opencv.core.Mat;

import java.util.Map;

/**
 * "Char Classifier" tab.
 *
 * <p>Left side: line/column/offset sliders, the selected line image with
 * column-grid overlay, the V-histogram, and the cropped character with its
 * classification result and Hu-moment values.
 *
 * <p>Right side: a {@code compareWithChars} text-field lets the user type
 * any subset of characters; the right panel shows a {@link CharFeatureCardBuilder}
 * card for each of those characters so the crop can be visually compared
 * against the template features.
 */
public class CharClassifierView {

    private static final double LINE_VIEW_W = 800;
    private static final double LINE_VIEW_H = 80;
    private static final double HIST_W      = LINE_VIEW_W;
    private static final double HIST_H      = 80;
    private static final double CHAR_VIEW_W = 160;
    private static final double CHAR_VIEW_H = 160;
    private static final int    COMPARE_SPACING = 6;

    @Getter
    private final HBox root = new HBox(12);

    // --- controls (left panel) ---
    private final Slider    lineSlider     = new Slider(0, 0, 0);
    private final Label     lineValLabel   = monoLabel("—");
    private final Slider    colSlider      = new Slider(0, 0, 0);
    private final Label     colValLabel    = monoLabel("—");
    private final Slider    offsetSlider   = new Slider(-50, 50, 0);
    private final Label     offsetValLabel = monoLabel("0");
    private final TextField compareField   = new TextField();

    // --- line image + column grid overlay ---
    private final ImageView lineView    = new ImageView();
    private final Canvas    lineOverlay = new Canvas(LINE_VIEW_W, LINE_VIEW_H);

    // --- vertical histogram of selected line ---
    private final Canvas histCanvas = new Canvas(HIST_W, HIST_H);

    // --- cropped char ---
    private final ImageView charView        = new ImageView();
    private final Label     classifiedLabel = monoLabel("");
    private final Label     huMomentsLabel  = monoLabel("");
    private final Label     infoLabel       = monoLabel("");

    // --- right panel: candidate template cards ---
    private final FlowPane  compareFlow     = new FlowPane(COMPARE_SPACING, COMPARE_SPACING);
    private final ScrollPane compareScroll  = new ScrollPane(compareFlow);

    // --- state ---
    private TextLineExtractionResult lastResult  = null;
    private GridDetectionResult      lastGrid    = null;
    private PreProcessingResult      lastPreProc = null;
    private int[]                    colStarts   = new int[0];
    private int                      charWidth   = 0;

    private final CharTemplateClassifier classifier;
    private final CharTemplateDb          db;

    // -------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------

    public CharClassifierView(ProcessingContext context,
                              CharTemplateClassifier classifier) {
        this.classifier = classifier;
        this.db         = classifier.getDb();

        context.textLinesProperty    .addListener((obs, o, r) -> { if (r != null) onResult(r); });
        context.gridDetectionProperty.addListener((obs, o, r) -> { lastGrid = r;    onGridOrPreProc(); });
        context.preProcessingProperty.addListener((obs, o, r) -> { lastPreProc = r; onGridOrPreProc(); });

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

        // sliders
        configureSlider(lineSlider,   1,  400, lineValLabel,   () -> refreshDisplay());
        configureSlider(colSlider,    5,  400, colValLabel,    () -> refreshDisplay());
        configureSlider(offsetSlider, 10, 400, offsetValLabel, () -> refreshDisplay());

        // compareWithChars field
        compareField.setPromptText("e.g. MNABab01");
        compareField.setPrefWidth(200);
        compareField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        compareField.textProperty().addListener((obs, o, n) -> rebuildComparePanel());

        // classified label style
        classifiedLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 36));
        classifiedLabel.setStyle("-fx-text-fill: #88ff88; -fx-font-family: monospace;");
        huMomentsLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-family: monospace; -fx-font-size: 11;");

        // compare panel
        compareFlow.setPadding(new Insets(6));
        compareFlow.setStyle("-fx-background-color: #1e1e1e;");
        compareScroll.setFitToWidth(false);
        compareScroll.setFitToHeight(false);
        compareScroll.setPrefWidth(420);
        compareScroll.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");

        // --- left panel layout ---
        HBox lineRow   = hrow(styledLabel("Line index:"),  lineSlider,   lineValLabel);
        HBox colRow    = hrow(styledLabel("Col index:"),   colSlider,    colValLabel);
        HBox offsetRow = hrow(styledLabel("Offset (px):"), offsetSlider, offsetValLabel);
        HBox compareRow = hrow(styledLabel("Compare with chars:"), compareField);

        StackPane lineStack = new StackPane(lineView, lineOverlay);
        lineStack.setAlignment(Pos.TOP_LEFT);
        lineStack.setMaxSize(LINE_VIEW_W, LINE_VIEW_H);

        VBox charPanel = panel("Char Crop", new VBox(4, charView, classifiedLabel, huMomentsLabel));
        VBox infoPanel = panel("Info", infoLabel);

        HBox cropRow = new HBox(12, charPanel, infoPanel);
        cropRow.setAlignment(Pos.TOP_LEFT);

        VBox leftPanel = new VBox(8,
                lineRow, colRow, offsetRow, compareRow,
                panel("V-Histogram (selected line)", histCanvas),
                panel("Selected Line + columns", lineStack),
                cropRow);
        leftPanel.setPadding(new Insets(10));
        leftPanel.setStyle("-fx-background-color: #1e1e1e;");

        // --- right panel label ---
        Label rightTitle = new Label("Candidate templates");
        rightTitle.setFont(Font.font("System", FontWeight.BOLD, 13));
        rightTitle.setStyle("-fx-text-fill: #bbbbbb;");
        VBox rightPanel = new VBox(4, rightTitle, compareScroll);
        rightPanel.setPadding(new Insets(10, 10, 10, 0));
        rightPanel.setStyle("-fx-background-color: #1e1e1e;");

        root.getChildren().addAll(leftPanel, rightPanel);
        root.setStyle("-fx-background-color: #1e1e1e;");
    }

    // -------------------------------------------------------------------------
    // update handlers
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

    private void onGridOrPreProc() {
        if (lastGrid == null || lastPreProc == null) return;

        double charWd = lastGrid.bestCharW();
        double x0d    = lastGrid.bestCharX0();
        int fw    = lastPreProc.frameWidth();
        int charW = (int) Math.max(1, Math.round(charWd));
        int x0    = (int) Math.round(x0d);

        int count = fw > 0 && charW > 0 ? (fw - x0 + charW - 1) / charW : 0;
        int[] starts = new int[count];
        for (int i = 0; i < count; i++) starts[i] = (int) Math.round(x0d + i * charWd);
        int backCount = charW > 0 ? x0 / charW : 0;
        if (backCount > 0) {
            int[] full = new int[backCount + count];
            for (int i = 0; i < backCount; i++) full[i] = (int) Math.round(x0d - (backCount - i) * charWd);
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
    // compare panel rebuild
    // -------------------------------------------------------------------------

    private void rebuildComparePanel() {
        compareFlow.getChildren().clear();
        String text = compareField.getText();
        if (text == null || text.isBlank()) return;

        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        for (char ch : text.toCharArray()) {
            PreComputedFeaturesChar feat = features.get(ch);
            if (feat != null) {
                compareFlow.getChildren().add(CharFeatureCardBuilder.buildCard(ch, feat));
            }
        }
    }

    // -------------------------------------------------------------------------
    // histogram drawing
    // -------------------------------------------------------------------------

    private void drawHistogram() {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, HIST_W, HIST_H);
        if (lastPreProc == null) return;

        float[] sums    = lastPreProc.vColSums();
        int[]   valleys = lastPreProc.vValleys();
        int     fw      = lastPreProc.frameWidth();
        if (sums == null || sums.length == 0 || fw == 0) return;

        double scaleX = HIST_W / (double) fw;
        double barH   = HIST_H - 4;

        float globalMax = 1f;
        for (float v : sums) if (v > globalMax) globalMax = v;

        for (int x = 0; x < sums.length; x++) {
            double px = x * scaleX;
            double bh = (sums[x] / globalMax) * barH;
            gc.setFill(Color.rgb(100, 160, 255, 0.85));
            gc.fillRect(px, HIST_H - bh, Math.max(1, scaleX), bh);
        }

        double vThreshY = HIST_H - (0.25 * barH);
        gc.setStroke(Color.rgb(255, 180, 0, 0.55));
        gc.setLineWidth(0.8);
        gc.strokeLine(0, vThreshY, HIST_W, vThreshY);

        if (valleys != null) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.2);
            for (int v : valleys) {
                double px = v * scaleX;
                gc.strokeLine(px, 0, px, HIST_H);
            }
        }

        if (lastGrid != null) {
            double charW = lastGrid.bestCharW();
            double x0    = lastGrid.bestCharX0();
            gc.setStroke(Color.rgb(180, 100, 255, 0.7));
            gc.setLineWidth(1.0);
            for (double x = x0; x < fw; x += charW) {
                double px = x * scaleX;
                gc.strokeLine(px, 0, px, HIST_H);
            }
            for (double x = x0 - charW; x >= 0; x -= charW) {
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
            classifiedLabel.setText("");
            huMomentsLabel.setText("");
            infoLabel.setText("No lines detected.");
            return;
        }

        int lineIdx = clampedLineIdx();
        TextLine line = lastResult.lines().get(lineIdx);
        WritableImage lineImg = line.lineImage();
        lineView.setImage(lineImg);

        if (lineImg == null || lineImg.getWidth() == 0) {
            charView.setImage(null);
            classifiedLabel.setText("");
            huMomentsLabel.setText("");
            infoLabel.setText("No image for line " + lineIdx);
            return;
        }

        int imgW = (int) lineImg.getWidth();
        int imgH = (int) lineImg.getHeight();

        double scaleX = Math.min(LINE_VIEW_W / imgW, LINE_VIEW_H / imgH);
        double rendW  = imgW * scaleX;
        double rendH  = imgH * scaleX;
        double offX   = (LINE_VIEW_W - rendW) / 2.0;
        double offY   = (LINE_VIEW_H - rendH) / 2.0;

        int offset = (int) offsetSlider.getValue();

        // all column grid lines — dim
        gc.setStroke(Color.rgb(80, 160, 255, 0.35));
        gc.setLineWidth(1.0);
        for (int cs : colStarts) {
            double px = offX + (cs + offset) * scaleX;
            if (px >= offX && px <= offX + rendW)
                gc.strokeLine(px, offY, px, offY + rendH);
        }

        int colIdx = colStarts.length == 0 ? -1 : (int) Math.round(
                Math.max(0, Math.min(colSlider.getValue(), colStarts.length - 1)));

        if (colIdx >= 0 && colIdx < colStarts.length) {
            int cxPx  = colStarts[colIdx] + offset;
            int cxEnd = (colIdx + 1 < colStarts.length)
                    ? colStarts[colIdx + 1] + offset
                    : cxPx + charWidth;

            double px1 = offX + cxPx  * scaleX;
            double px2 = offX + cxEnd * scaleX;

            gc.setFill(Color.rgb(255, 220, 0, 0.18));
            gc.fillRect(px1, offY, px2 - px1, rendH);
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.5);
            gc.strokeLine(px1, offY, px1, offY + rendH);

            int cropX = Math.max(0, cxPx);
            int cropW = Math.max(1, Math.min(cxEnd - cxPx, imgW - cropX));
            if (cropW > 0 && imgH > 0) {
                WritableImage cropImg = new WritableImage(lineImg.getPixelReader(), cropX, 0, cropW, imgH);
                charView.setImage(cropImg);

                Mat greyMat = FxImageUtils.writableImageToGreyMat(cropImg);
                try {
                    CharTemplateClassifier.Result r = classifier.classify(greyMat);
                    classifiedLabel.setText(r.toString());
                    double[] hu = CharTemplateDb.computeHuMoments(greyMat);
                    huMomentsLabel.setText(formatHuMoments(hu));
                } finally {
                    greyMat.release();
                }
            }

            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n" +
                    "Col %d/%d  x=%d–%d  w=%dpx%n" +
                    "charWidth=%dpx  offset=%dpx",
                    lineIdx, line.rowStart(), line.rowEnd(), line.height(),
                    colIdx, colStarts.length, cxPx, cxEnd, cropW,
                    charWidth, offset));
        } else {
            charView.setImage(null);
            classifiedLabel.setText("");
            huMomentsLabel.setText("");
            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n%d columns  charWidth=%dpx",
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

    private static void configureSlider(Slider s, double majorTick, double prefWidth,
                                        Label valLabel, Runnable onChange) {
        s.setShowTickLabels(true);
        s.setMajorTickUnit(majorTick);
        s.setSnapToTicks(majorTick == 1 || majorTick == 5);
        s.setPrefWidth(prefWidth);
        s.valueProperty().addListener((obs, o, n) -> {
            valLabel.setText(String.valueOf(n.intValue()));
            onChange.run();
        });
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

    private static String formatHuMoments(double[] hu) {
        if (hu == null || hu.length < 7) return "Hu: n/a";
        StringBuilder sb = new StringBuilder("Hu moments (log-scaled):\n");
        for (int i = 0; i < 7; i++) {
            sb.append(String.format(" h%d=%8.3f", i + 1, hu[i]));
            if (i % 2 == 1) sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }
}
