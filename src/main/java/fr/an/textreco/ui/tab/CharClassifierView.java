package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.CharTemplateClassifier;
import fr.an.textreco.processing.CharTemplateDb;
import fr.an.textreco.ui.viewmodel.CharClassifierViewModel;
import fr.an.textreco.processing.PreComputedFeaturesChar;
import fr.an.textreco.util.FxImageUtils;
import fr.an.textreco.util.MatTracker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
 * <p>Left side: line/column navigation sliders, per-axis offset controls
 * (computed value + force checkbox + forced value for line/Y and column/X),
 * the selected line image with column-grid overlay, the V-histogram, and the
 * cropped character with its classification result and Hu-moment values.
 *
 * <p>Right side: a {@code compareWithChars} text-field lets the user type
 * any subset of characters; the right panel shows a {@link CharFeatureCardBuilder}
 * card for each of those characters so the crop can be visually compared
 * against the template features.
 */
public class CharClassifierView extends VBox implements JavaView<CharClassifierViewModel> {

    private static final double LINE_VIEW_W = 800;
    private static final double LINE_VIEW_H = 40;
    private static final double HIST_W = LINE_VIEW_W;
    private static final double HIST_H = 40;
    private static final double TICK_H = 8;   // height of valley/grid tick marks
    private static final int COMPARE_SPACING = 6;

    @Getter
    private final VBox root = new VBox(8);

    // --- controls (left panel) ---
    private final Slider lineSlider = new Slider(0, 0, 0);
    private final Label lineValLabel = monoLabel("—");
    private final Slider colSlider = new Slider(0, 0, 0);
    private final Label colValLabel = monoLabel("—");

    // --- per-axis offset controls: computed label + force checkbox + forced value ---
    private final Label lineOffComputedLabel = monoLabel("—");
    private final CheckBox forceLineOffCb = styledCheckBox("Force");
    private final Spinner<Integer> forcedLineOffSp = offsetSpinner();
    private final Label colOffComputedLabel = monoLabel("—");
    private final CheckBox forceColOffCb = styledCheckBox("Force");
    private final Spinner<Integer> forcedColOffSp = offsetSpinner();

    private final TextField compareField = new TextField();

    // --- line image + column grid overlay ---
    private final ImageView lineView = new ImageView();
    private final Canvas lineOverlay = new Canvas(LINE_VIEW_W, LINE_VIEW_H);

    // --- recognised chars drawn below the line image, one per column ---
    private static final double CHARS_ROW_H = 16;
    private final Canvas lineCharsCanvas = new Canvas(LINE_VIEW_W, CHARS_ROW_H);

    // --- vertical histogram of selected line ---
    private final Canvas histCanvas = new Canvas(HIST_W, HIST_H);

    // --- cropped char (rich card, rebuilt on every refresh) ---
    private final VBox cropCardHolder = new VBox();
    private final Label infoLabel = monoLabel("");

    // --- best-match result panel (rebuilt on every refresh) ---
    private final VBox bestMatchHolder = new VBox(4);

    // --- top candidates panel: 3 cards left-to-right (rebuilt on every refresh) ---
    private final HBox candidatesFlow = new HBox(COMPARE_SPACING);

    // --- right panel: candidate template cards ---
    private final FlowPane compareFlow = new FlowPane(COMPARE_SPACING, COMPARE_SPACING);
    private final ScrollPane compareScroll = new ScrollPane(compareFlow);

    // --- state ---
    private TextLineExtractionResult lastResult = null;
    private GridDetectionResult lastGrid = null;
    private PreProcessingResult lastPreProc = null;
    private int[] colStarts = new int[0];
    private int charWidth = 0;

    // cache: recognised char for every column of the last classified line
    private char[] lineChars = new char[0];
    private int lineCharsForLine = -1;
    private int lineCharsForXOffset = Integer.MIN_VALUE;
    private int lineCharsForYOffset = Integer.MIN_VALUE;

    private final CharTemplateClassifier classifier;
    private final CharTemplateDb db;

    // -------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------

    public CharClassifierView(CharClassifierViewModel viewModel) {
        this.classifier = viewModel.getCharClassifier();
        this.db = classifier.getDb();

        viewModel.textLinesProperty().addListener((obs, o, r) -> {
            if (r != null) onResult(r);
        });
        viewModel.gridDetectionProperty().addListener((obs, o, r) -> {
            lastGrid = r;
            onGridOrPreProc();
        });
        viewModel.preProcessingProperty().addListener((obs, o, r) -> {
            lastPreProc = r;
            onGridOrPreProc();
        });

        // Refresh once when this tab becomes the visible one. TabPaneSkin toggles
        // the visibility of unselected tab content, so visibleProperty tracks
        // selection; refreshing only while visible avoids rebuilding a hidden
        // tab's node graph on every pipeline frame (the churn that triggered the
        // intermittent Scene.synchronizeSceneNodes NPE).
        root.visibleProperty().addListener((obs, o, visible) -> {
            if (visible) requestRefresh();
        });

        lineView.setPreserveRatio(true);
        lineView.setFitWidth(LINE_VIEW_W);
        lineView.setFitHeight(LINE_VIEW_H);
        lineView.setSmooth(false);
        lineOverlay.setMouseTransparent(true);

        histCanvas.setStyle("-fx-background-color: #1a1a1a;");

        // sliders — compact width
        configureSlider(lineSlider, 1, 160, lineValLabel, this::requestRefresh);
        configureSlider(colSlider, 5, 160, colValLabel, this::requestRefresh);

        // per-axis offset controls: spinner enabled only while "Force" is ticked,
        // any change (toggle or value) triggers a refresh.
        forcedLineOffSp.disableProperty().bind(forceLineOffCb.selectedProperty().not());
        forcedColOffSp.disableProperty().bind(forceColOffCb.selectedProperty().not());
        forceLineOffCb.selectedProperty().addListener((obs, o, n) -> requestRefresh());
        forceColOffCb.selectedProperty().addListener((obs, o, n) -> requestRefresh());
        forcedLineOffSp.valueProperty().addListener((obs, o, n) -> requestRefresh());
        forcedColOffSp.valueProperty().addListener((obs, o, n) -> requestRefresh());

        // compareWithChars field
        compareField.setPromptText("e.g. MNABab01");
        compareField.setPrefWidth(200);
        compareField.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        compareField.textProperty().addListener((obs, o, n) -> rebuildComparePanel());

        // compare panel
        compareFlow.setPadding(new Insets(6));
        compareFlow.setStyle("-fx-background-color: #1e1e1e;");
        compareScroll.setFitToWidth(true);
        compareScroll.setFitToHeight(false);
        compareScroll.setMinWidth(300);
        compareScroll.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");

        // --- controls + line view (top strip) ---
        // line/column navigation sliders on one row …
        HBox navRow = hrow(
                styledLabel("Line:"), lineSlider, lineValLabel,
                styledLabel("Col:"), colSlider, colValLabel);

        // … and the two offset control groups (computed value + force + forced value) on the next.
        HBox lineOffGroup = hrow(
                styledLabel("Line offset:"), lineOffComputedLabel,
                forceLineOffCb, forcedLineOffSp, styledLabel("px"));
        HBox colOffGroup = hrow(
                styledLabel("Col offset:"), colOffComputedLabel,
                forceColOffCb, forcedColOffSp, styledLabel("px"));
        HBox offsetRow = hrow(lineOffGroup, styledLabel("   "), colOffGroup);

        StackPane lineStack = new StackPane(lineView, lineOverlay);
        lineStack.setAlignment(Pos.TOP_LEFT);
        lineStack.setMaxSize(LINE_VIEW_W, LINE_VIEW_H);

        lineCharsCanvas.setStyle("-fx-background-color: #111111;");
        VBox lineAndChars = new VBox(0, lineStack, lineCharsCanvas);
        lineAndChars.setAlignment(Pos.TOP_LEFT);

        VBox topPanel = new VBox(8,
                navRow,
                offsetRow,
                panel("V-Histogram (selected line)", histCanvas),
                panel("Selected Line + columns", lineAndChars));
        topPanel.setPadding(new Insets(10, 10, 0, 10));
        topPanel.setStyle("-fx-background-color: #1e1e1e;");

        // --- bottom strip: crop (left) | best-match + top-N candidates (centre/right) ---
        cropCardHolder.setAlignment(Pos.TOP_LEFT);
        cropCardHolder.setSpacing(4);

        VBox cropPanel = new VBox(4, panel("Char Crop", cropCardHolder), panel("Info", infoLabel));
        cropPanel.setAlignment(Pos.TOP_LEFT);
        cropPanel.setPadding(new Insets(0, 8, 10, 10));

        // best-match panel: glyph + per-feature scores
        bestMatchHolder.setAlignment(Pos.TOP_LEFT);
        bestMatchHolder.setPadding(new Insets(4));

        // top candidates flow (auto-populated from classifyTopN)
        candidatesFlow.setPadding(new Insets(4));
        candidatesFlow.setAlignment(Pos.TOP_LEFT);
        candidatesFlow.setStyle("-fx-background-color: #1e1e1e;");

        VBox bestAndCandidatesPanel = new VBox(6,
                panel("Best Match", bestMatchHolder),
                panel("Top Candidates", candidatesFlow));
        bestAndCandidatesPanel.setAlignment(Pos.TOP_LEFT);
        bestAndCandidatesPanel.setPadding(new Insets(0, 8, 10, 0));
        // 3 cards × (HHIST_W=56 + tmpl=32 + hbox-gap=3 + card-pad=10 + border=2)
        bestAndCandidatesPanel.setPrefWidth(480);

        // manual compare panel (right-most, optional) — fixed width
        Label rightTitle = new Label("Compare with chars:");
        rightTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        rightTitle.setStyle("-fx-text-fill: #bbbbbb;");
        VBox candidatePanel = new VBox(4, hrow(rightTitle, compareField), compareScroll);
        candidatePanel.setPadding(new Insets(0, 10, 10, 0));
        candidatePanel.setStyle("-fx-background-color: #1e1e1e;");
        // candidatePanel.setPrefWidth(260);
        HBox.setHgrow(candidatePanel, javafx.scene.layout.Priority.ALWAYS);

        HBox bottomStrip = new HBox(8, cropPanel, bestAndCandidatesPanel, candidatePanel);
        bottomStrip.setAlignment(Pos.TOP_LEFT);
        bottomStrip.setStyle("-fx-background-color: #1e1e1e;");

        root.getChildren().addAll(topPanel, bottomStrip);
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
        requestRefresh();
    }

    private void onGridOrPreProc() {
        if (lastGrid == null || lastPreProc == null) return;

        double charWd = lastGrid.bestCharW();
        double x0d = lastGrid.bestCharX0();
        int fw = lastPreProc.frameWidth();
        int charW = (int) Math.max(1, Math.round(charWd));
        int x0 = (int) Math.round(x0d);

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

        // computed (optimal) offsets from the grid model — shown next to the
        // force controls so the user sees the auto value before overriding it.
        lineOffComputedLabel.setText(String.format("computed=%.2f", lastGrid.bestLineY0()));
        colOffComputedLabel.setText(String.format("computed=%.2f", lastGrid.bestCharX0()));

        int numCols = colStarts.length;
        colSlider.setMax(numCols == 0 ? 0 : numCols - 1);
        if (colSlider.getValue() >= numCols) colSlider.setValue(0);

        requestRefresh();
    }

    // -------------------------------------------------------------------------
    // coalesced refresh
    // -------------------------------------------------------------------------

    /**
     * Whether a {@link #refreshDisplay()} pass is already queued for the next
     * pulse.  The pipeline pushes several property updates per frame (pre-proc,
     * grid, lines), each of which would otherwise trigger its own full
     * scene-graph rebuild; coalescing collapses them into a single refresh and
     * defers the heavy node mutation out of the property-set call chain.
     */
    private boolean refreshPending = false;

    /**
     * Requests a single deferred {@link #refreshDisplay()} on the FX thread.
     * Multiple calls before the next pulse collapse into one. No-op while this
     * tab is not the visible one — it will refresh when selected (see the
     * {@code visibleProperty} listener in the constructor).
     */
    private void requestRefresh() {
        if (refreshPending || !isShowing()) return;
        refreshPending = true;
        javafx.application.Platform.runLater(() -> {
            refreshPending = false;
            if (isShowing()) refreshDisplay();
        });
    }

    /**
     * True when this tab's content is attached to a scene and visible (i.e. the
     * selected tab).  Refreshing only while showing avoids rebuilding the node
     * graph on every pipeline frame when another tab is in front.
     */
    private boolean isShowing() {
        return root.getScene() != null && root.isVisible();
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

    /**
     * Redraws the V-histogram canvas using the same {@code offX}/{@code scaleX}
     * transform as the line image, so histogram bars align with the pixels above.
     */
    private void drawHistogram(double offX, double scaleX) {
        GraphicsContext gc = histCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, HIST_W, HIST_H);
        if (lastPreProc == null) return;

        float[] sums = lastPreProc.vColSums();
        int[] valleys = lastPreProc.vValleys();
        int fw = lastPreProc.frameWidth();
        if (sums == null || sums.length == 0 || fw == 0) return;

        double barH = HIST_H - 4;

        float globalMax = 1f;
        for (float v : sums) if (v > globalMax) globalMax = v;

        for (int x = 0; x < sums.length; x++) {
            double px = offX + x * scaleX;
            double bh = (sums[x] / globalMax) * barH;
            gc.setFill(Color.rgb(100, 160, 255, 0.85));
            gc.fillRect(px, HIST_H - bh, Math.max(1, scaleX), bh);
        }

        double vThreshY = HIST_H - (0.25 * barH);
        gc.setStroke(Color.rgb(255, 180, 0, 0.55));
        gc.setLineWidth(0.8);
        gc.strokeLine(offX, vThreshY, offX + fw * scaleX, vThreshY);

        if (valleys != null) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.2);
            for (int v : valleys) {
                double px = offX + v * scaleX;
                gc.strokeLine(px, 0, px, TICK_H);
            }
        }

        if (lastGrid != null && lastGrid.bestCharW() > 0) {
            double charW = lastGrid.bestCharW();
            double x0 = lastGrid.bestCharX0();
            gc.setStroke(Color.rgb(180, 100, 255, 0.85));
            gc.setLineWidth(1.0);
            for (double x = x0; x < fw; x += charW) {
                double px = offX + x * scaleX;
                gc.strokeLine(px, 0, px, TICK_H);
            }
            for (double x = x0 - charW; x >= 0; x -= charW) {
                double px = offX + x * scaleX;
                gc.strokeLine(px, 0, px, TICK_H);
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
            cropCardHolder.getChildren().clear();
            bestMatchHolder.getChildren().clear();
            candidatesFlow.getChildren().clear();
            clearLineCharsCanvas();
            infoLabel.setText("No lines detected.");
            return;
        }

        int lineIdx = clampedLineIdx();
        TextLine line = lastResult.lines().get(lineIdx);
        WritableImage lineImg = line.lineImage();
        lineView.setImage(lineImg);

        if (lineImg == null || lineImg.getWidth() == 0) {
            cropCardHolder.getChildren().clear();
            bestMatchHolder.getChildren().clear();
            candidatesFlow.getChildren().clear();
            clearLineCharsCanvas();
            infoLabel.setText("No image for line " + lineIdx);
            return;
        }

        int imgW = (int) lineImg.getWidth();
        int imgH = (int) lineImg.getHeight();

        double scaleX = Math.min(LINE_VIEW_W / imgW, LINE_VIEW_H / imgH);
        double rendW = imgW * scaleX;
        double rendH = imgH * scaleX;
        double offX = (LINE_VIEW_W - rendW) / 2.0;
        double offY = (LINE_VIEW_H - rendH) / 2.0;

        // histogram aligned with line image
        drawHistogram(offX, scaleX);

        int xOffset = effectiveColOffset();
        int yOffset = effectiveLineOffset();

        // column grid: short ticks at the top of the rendered image area — dim blue
        gc.setStroke(Color.rgb(80, 160, 255, 0.6));
        gc.setLineWidth(1.0);
        for (int cs : colStarts) {
            double px = offX + (cs + xOffset) * scaleX;
            if (px >= offX && px <= offX + rendW)
                gc.strokeLine(px, offY, px, offY + TICK_H);
        }

        // --- classify every column and draw best char below the line image ---
        classifyAllColumns(lineImg, imgW, imgH, lineIdx, xOffset, yOffset, offX, scaleX);

        int colIdx = colStarts.length == 0 ? -1 : (int) Math.round(
                Math.max(0, Math.min(colSlider.getValue(), colStarts.length - 1)));

        if (colIdx >= 0 && colIdx < colStarts.length) {
            int cxPx = colStarts[colIdx] + xOffset;
            int cxEnd = (colIdx + 1 < colStarts.length)
                    ? colStarts[colIdx + 1] + xOffset
                    : cxPx + charWidth;

            double px1 = offX + cxPx * scaleX;
            double px2 = offX + cxEnd * scaleX;

            gc.setFill(Color.rgb(255, 220, 0, 0.18));
            gc.fillRect(px1, offY, px2 - px1, rendH);
            gc.setStroke(Color.rgb(255, 220, 0, 0.9));
            gc.setLineWidth(1.5);
            gc.strokeLine(px1, offY, px1, offY + rendH);

            int cropX = Math.max(0, cxPx);
            int cropW = Math.min(Math.max(1, cxEnd - cxPx), imgW - cropX);
            if (cropW > 0 && imgH > 0 && cropX < imgW) {
                Mat greyMat = readCropToMat(lineImg.getPixelReader(), cropX, imgW, cropW, imgH, yOffset);
                try {
                    CharTemplateClassifier.Result r = classifier.classify(greyMat);

                    // --- crop card (live features of the cropped image) ---
                    PreComputedFeaturesChar liveFeat = db.computeAllFeatures(greyMat);
                    VBox cropCard = CharFeatureCardBuilder.buildCard(r.ch(), liveFeat);
                    cropCardHolder.getChildren().setAll(cropCard);
                    liveFeat.tmpl().release();

                    // --- best-match panel (top-3 scores) ---
                    bestMatchHolder.getChildren().setAll(buildBestMatchPanel(r));

                    // --- top-3 candidate cards ---
                    candidatesFlow.getChildren().clear();
                    Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
                    for (CharTemplateClassifier.TopEntry entry : r.top3()) {
                        PreComputedFeaturesChar feat = features.get(entry.ch());
                        if (feat != null) {
                            candidatesFlow.getChildren().add(
                                    CharFeatureCardBuilder.buildCard(entry.ch(), feat));
                        }
                    }
                } finally {
                    MatTracker.free(greyMat, "CharClassifierView.readCropToMat");
                    greyMat.release();
                }
            } else {
                cropCardHolder.getChildren().clear();
                bestMatchHolder.getChildren().clear();
                candidatesFlow.getChildren().clear();
            }

            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n" +
                            "Col %d/%d  x=%d–%d  w=%dpx%n" +
                            "charWidth=%dpx  xOffset=%dpx  yOffset=%dpx",
                    lineIdx, line.rowStart(), line.rowEnd(), line.height(),
                    colIdx, colStarts.length, cxPx, cxEnd, cropW,
                    charWidth, xOffset, yOffset));
        } else {
            cropCardHolder.getChildren().clear();
            infoLabel.setText(String.format(
                    "Line %d  y=%d–%d  h=%dpx%n%d columns  charWidth=%dpx",
                    lineIdx, line.rowStart(), line.rowEnd(), line.height(),
                    colStarts.length, charWidth));
        }
    }

    // -------------------------------------------------------------------------
    // full-line char recognition row
    // -------------------------------------------------------------------------

    /**
     * Classifies every column of the given line image and redraws
     * {@link #lineCharsCanvas} with the best-match char centred over each
     * column's x-position (using the same {@code offX}/{@code scaleX} as the
     * line overlay so glyphs align pixel-perfectly with the image above).
     *
     * <p>Results are cached by {@code lineIdx} + x/y offsets: if none changed
     * since the last call the canvas is just redrawn from the cache.
     */
    // -------------------------------------------------------------------------
    // pixel helpers (no intermediate WritableImage)
    // -------------------------------------------------------------------------

    // scratch buffer reused across classifyAllColumns calls — grows but never shrinks
    private byte[] classifyBgraRow = new byte[0];

    /**
     * Reads a rectangle from {@code pr} into a greyscale OpenCV Mat without
     * creating an intermediate WritableImage.  Caller must {@code release()} the result.
     *
     * <p>{@code yOffset} shifts the vertical read window: source row {@code y} is
     * read from {@code y + yOffset}.  Rows that fall outside the image are left
     * black so the output Mat keeps the full {@code imgH} height (the classifier
     * expects a fixed line-height crop).
     */
    private Mat readCropToMat(javafx.scene.image.PixelReader pr,
                              int cropX, int imgW, int cropW, int imgH, int yOffset) {
        int safeCropW = Math.min(cropW, imgW - cropX);
        if (safeCropW <= 0 || imgH <= 0) return new Mat();
        int rowBytes = safeCropW * 4;
        if (classifyBgraRow.length < rowBytes) classifyBgraRow = new byte[rowBytes];
        byte[] grey = new byte[safeCropW * imgH];
        for (int y = 0; y < imgH; y++) {
            int srcY = y + yOffset;
            if (srcY < 0 || srcY >= imgH) continue; // leave row black (out of band)
            pr.getPixels(cropX, srcY, safeCropW, 1,
                    javafx.scene.image.PixelFormat.getByteBgraInstance(),
                    classifyBgraRow, 0, rowBytes);
            for (int x = 0; x < safeCropW; x++) {
                int b = classifyBgraRow[x * 4] & 0xFF;
                int g = classifyBgraRow[x * 4 + 1] & 0xFF;
                int r = classifyBgraRow[x * 4 + 2] & 0xFF;
                grey[y * safeCropW + x] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
            }
        }
        Mat mat = new Mat(imgH, safeCropW, org.opencv.core.CvType.CV_8UC1);
        MatTracker.alloc(mat, "CharClassifierView.readCropToMat");
        mat.put(0, 0, grey);
        return mat;
    }

    private void classifyAllColumns(WritableImage lineImg,
                                    int imgW, int imgH,
                                    int lineIdx, int xOffset, int yOffset,
                                    double offX, double scaleX) {
        // re-classify only when line or either offset changed
        if (lineIdx != lineCharsForLine
                || xOffset != lineCharsForXOffset || yOffset != lineCharsForYOffset
                || lineChars.length != colStarts.length) {
            lineCharsForLine = lineIdx;
            lineCharsForXOffset = xOffset;
            lineCharsForYOffset = yOffset;
            lineChars = new char[colStarts.length];

            // ensure scratch row buffer is large enough for the widest possible crop
            int rowBytes = imgW * 4;
            if (classifyBgraRow.length < rowBytes) classifyBgraRow = new byte[rowBytes];

            javafx.scene.image.PixelReader pr = lineImg.getPixelReader();
            Mat greyMat = new Mat();
            MatTracker.alloc(greyMat, "classifyAllColumns");
            try {
                for (int i = 0; i < colStarts.length; i++) {
                    int cxPx = colStarts[i] + xOffset;
                    int cxEnd = (i + 1 < colStarts.length)
                            ? colStarts[i + 1] + xOffset
                            : cxPx + charWidth;
                    int cropX = Math.max(0, cxPx);
                    if (cropX >= imgW || imgH <= 0) {
                        lineChars[i] = ' ';
                        continue;
                    }
                    int cropW = Math.min(Math.max(1, cxEnd - cxPx), imgW - cropX);
                    if (cropW <= 0) {
                        lineChars[i] = ' ';
                        continue;
                    }

                    // read pixels directly into a grey Mat — no intermediate WritableImage.
                    // yOffset shifts the vertical read window; rows outside the band
                    // stay black so the crop keeps full imgH height.
                    byte[] grey = new byte[cropW * imgH];
                    for (int y = 0; y < imgH; y++) {
                        int srcY = y + yOffset;
                        if (srcY < 0 || srcY >= imgH) continue;
                        pr.getPixels(cropX, srcY, cropW, 1,
                                javafx.scene.image.PixelFormat.getByteBgraInstance(),
                                classifyBgraRow, 0, cropW * 4);
                        for (int x = 0; x < cropW; x++) {
                            int b = classifyBgraRow[x * 4] & 0xFF;
                            int g = classifyBgraRow[x * 4 + 1] & 0xFF;
                            int r = classifyBgraRow[x * 4 + 2] & 0xFF;
                            grey[y * cropW + x] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
                        }
                    }
                    greyMat.create(imgH, cropW, org.opencv.core.CvType.CV_8UC1);
                    greyMat.put(0, 0, grey);

                    CharTemplateClassifier.Result r = classifier.classify(greyMat);
                    lineChars[i] = r.isConfident() ? r.ch() : '?';
                }
            } finally {
                MatTracker.free(greyMat, "classifyAllColumns");
                greyMat.release();
            }
        }

        // redraw canvas
        GraphicsContext gc = lineCharsCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(17, 17, 17));
        gc.fillRect(0, 0, LINE_VIEW_W, CHARS_ROW_H);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 12));

        for (int i = 0; i < colStarts.length; i++) {
            int cxPx = colStarts[i] + xOffset;
            int cxEnd = (i + 1 < colStarts.length)
                    ? colStarts[i + 1] + xOffset
                    : cxPx + charWidth;
            double px = offX + (cxPx + cxEnd) / 2.0 * scaleX;   // centre of the column
            char ch = lineChars[i];

            gc.setFill(ch == '?' ? Color.rgb(180, 80, 80) : Color.rgb(160, 255, 160));
            // draw char centred horizontally at px; baseline near bottom of canvas
            gc.fillText(String.valueOf(ch), px - 4, CHARS_ROW_H - 2);
        }
    }

    private void clearLineCharsCanvas() {
        lineChars = new char[0];
        lineCharsForLine = -1;
        lineCharsForXOffset = Integer.MIN_VALUE;
        lineCharsForYOffset = Integer.MIN_VALUE;
        GraphicsContext gc = lineCharsCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(17, 17, 17));
        gc.fillRect(0, 0, LINE_VIEW_W, CHARS_ROW_H);
    }

    // -------------------------------------------------------------------------
    // best-match panel builder
    // -------------------------------------------------------------------------

    /**
     * Builds a compact panel showing the best glyph large and the top-3
     * candidates with their scores sorted best-first.
     *
     * <p>Layout:
     * <pre>
     *   ┌──────────────────────────┐
     *   │  A   (large, green)      │
     *   │  score: +0.923           │
     *   │──────────────────────────│
     *   │  #  ch  score            │
     *   │  1   A  +0.923           │
     *   │  2   R  +0.871           │
     *   │  3   M  +0.754           │
     *   └──────────────────────────┘
     * </pre>
     */
    private javafx.scene.Node buildBestMatchPanel(CharTemplateClassifier.Result r) {
        CharTemplateClassifier.TopEntry[] top3 = r.top3();

        if (top3 == null || top3.length == 0) {
            Label none = monoLabel("No candidates");
            none.setStyle("-fx-text-fill: #888888;");
            return none;
        }

        // large best glyph
        Label glyphLabel = new Label(String.valueOf(r.ch()));
        glyphLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 36));
        glyphLabel.setStyle(r.isConfident()
                ? "-fx-text-fill: #88ff88;"
                : "-fx-text-fill: #ff8844;");

        Label scoreLabel = new Label(String.format("score: %+.4f", r.score()));
        scoreLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-family: monospace; -fx-font-size: 11;");

        HBox topRow = new HBox(10, glyphLabel, scoreLabel);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // table header
        Label header = monoLabel(String.format("%-2s  %-3s  %-8s", "#", "ch", "score"));
        header.setStyle("-fx-text-fill: #888888; -fx-font-family: monospace; -fx-font-size: 10;"
                + "-fx-border-color: #444444; -fx-border-width: 0 0 1 0;");

        VBox rows = new VBox(1, topRow, header);
        for (int i = 0; i < top3.length; i++) {
            CharTemplateClassifier.TopEntry e = top3[i];
            Label row = new Label(String.format("%-2d  %-3c  %+.4f", i + 1, e.ch(), e.score()));
            row.setStyle("-fx-font-family: monospace; -fx-font-size: 10; -fx-text-fill: "
                    + (i == 0 ? "#ffffff;" : "#aaaaaa;"));
            rows.getChildren().add(row);
        }
        rows.setPadding(new Insets(4));
        return rows;
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

    private static CheckBox styledCheckBox(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setStyle("-fx-text-fill: #cccccc;");
        return cb;
    }

    private static Spinner<Integer> offsetSpinner() {
        Spinner<Integer> s = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(-500, 500, 0));
        s.setEditable(true);
        s.setPrefWidth(80);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return s;
    }

    /**
     * Horizontal crop delta (px) added to the grid's column starts.  Zero when
     * not forced (use the computed {@code bestCharX0}); otherwise the forced x0
     * minus the computed one, so {@code colStarts + delta} lands on the forced
     * grid.
     */
    private int effectiveColOffset() {
        if (!forceColOffCb.isSelected() || lastGrid == null) return 0;
        return forcedColOffSp.getValue() - (int) Math.round(lastGrid.bestCharX0());
    }

    /**
     * Vertical crop delta (px) shifting the column crop window within the line
     * image.  Zero when not forced (use the computed {@code bestLineY0});
     * otherwise the forced y0 minus the computed one.
     */
    private int effectiveLineOffset() {
        if (!forceLineOffCb.isSelected() || lastGrid == null) return 0;
        return forcedLineOffSp.getValue() - (int) Math.round(lastGrid.bestLineY0());
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }

}

