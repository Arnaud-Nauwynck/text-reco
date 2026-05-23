package fr.an.textreco.ui.tab;

import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.processing.PreProcessingProcessor;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class PreProcessingTab {

    private static final double BIG_W   = 320;
    private static final double BIG_H   = 240;
    private static final double HIST_H  = 240;   // height of h-histo canvas (matches BIG_H)
    private static final double HIST_W  = 100;   // width  of h-histo (horizontal bars)
    private static final double VHIST_W = 320;   // width  of v-histo canvas (matches BIG_W)
    private static final double VHIST_H = 60;
    private static final double MORPH_W = 200;
    private static final double MORPH_H = 150;

    @Getter
    private final VBox root = new VBox(8);

    private final ImageView binaryView    = imageView(BIG_W,   BIG_H);
    private final Canvas    hHistCanvas   = new Canvas(HIST_W, HIST_H);
    private final Canvas    vHistCanvas   = new Canvas(VHIST_W, VHIST_H);
    private final ImageView morphHView    = imageView(MORPH_W, MORPH_H);
    private final ImageView morphVView    = imageView(MORPH_W, MORPH_H);
    private final ImageView morphFwdView  = imageView(MORPH_W, MORPH_H);
    private final ImageView morphBwdView  = imageView(MORPH_W, MORPH_H);

    private final Label seLabel = statLabel("15");

    public PreProcessingTab(PreProcessingProcessor processor) {
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #1e1e1e;");

        // --- top row: binary + histograms ---
        VBox binaryPanel  = panel("Binary",          binaryView);
        VBox hHistPanel   = panel("H-projection",    hHistCanvas);
        hHistCanvas.setStyle("-fx-background-color:#1a1a1a;");

        // v-histo sits below binary; group them
        VBox vHistPanel   = panel("V-projection", vHistCanvas);
        vHistCanvas.setStyle("-fx-background-color:#1a1a1a;");

        VBox leftCol = new VBox(6, binaryPanel, vHistPanel);
        HBox topRow  = new HBox(8, leftCol, hHistPanel);
        topRow.setAlignment(Pos.TOP_LEFT);

        // --- binarisation controls ---
        Label methodLabel = new Label("Binarization:");
        methodLabel.setStyle("-fx-text-fill: #aaaaaa;");
        ComboBox<BinarizationMethod> methodCombo = new ComboBox<>(
                FXCollections.observableArrayList(BinarizationMethod.values()));
        methodCombo.setValue(processor.getBinarizationMethod());
        methodCombo.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd;");

        Label thRadLabel  = new Label("Top-hat radius:");
        thRadLabel.setStyle("-fx-text-fill: #aaaaaa;");
        Label thRadVal    = statLabel(String.valueOf(processor.getTophatRadius()));
        Slider thRadSlider = slider(1, 40, processor.getTophatRadius(), 10);
        thRadSlider.valueProperty().addListener((obs, o, n) -> {
            processor.setTophatRadius(n.intValue());
            thRadVal.setText(String.valueOf(n.intValue()));
        });

        Label thThrLabel  = new Label("Top-hat threshold:");
        thThrLabel.setStyle("-fx-text-fill: #aaaaaa;");
        Label thThrVal    = statLabel(String.valueOf(processor.getTophatThreshold()));
        Slider thThrSlider = slider(1, 100, processor.getTophatThreshold(), 20);
        thThrSlider.valueProperty().addListener((obs, o, n) -> {
            processor.setTophatThreshold(n.intValue());
            thThrVal.setText(String.valueOf(n.intValue()));
        });

        Label adaptBlockLabel = new Label("Adaptive block:");
        adaptBlockLabel.setStyle("-fx-text-fill: #aaaaaa;");
        Label adaptBlockVal   = statLabel(String.valueOf(processor.getAdaptiveBlock()));
        Slider adaptBlockSlider = slider(3, 99, processor.getAdaptiveBlock(), 20);
        adaptBlockSlider.valueProperty().addListener((obs, o, n) -> {
            processor.setAdaptiveBlock(n.intValue());
            adaptBlockVal.setText(String.valueOf(n.intValue()));
        });

        // show/hide sliders depending on chosen method
        VBox tophatParams  = new VBox(4,
                hrow(thRadLabel,     thRadSlider,     thRadVal),
                hrow(thThrLabel,     thThrSlider,     thThrVal));
        VBox adaptiveParams = new VBox(4,
                hrow(adaptBlockLabel, adaptBlockSlider, adaptBlockVal));

        Runnable updateVisibility = () -> {
            BinarizationMethod m = methodCombo.getValue();
            tophatParams  .setVisible(m == BinarizationMethod.TOPHAT);
            tophatParams  .setManaged(m == BinarizationMethod.TOPHAT);
            adaptiveParams.setVisible(m == BinarizationMethod.ADAPTIVE);
            adaptiveParams.setManaged(m == BinarizationMethod.ADAPTIVE);
        };
        methodCombo.setOnAction(e -> {
            processor.setBinarizationMethod(methodCombo.getValue());
            updateVisibility.run();
        });
        updateVisibility.run();

        VBox binarizeBox = new VBox(4,
                hrow(methodLabel, methodCombo),
                tophatParams, adaptiveParams);

        // --- SE size slider ---
        Label seTitle = new Label("Morph SE half-length:");
        seTitle.setStyle("-fx-text-fill: #aaaaaa;");
        Slider seSlider = slider(1, 20, processor.getSeHalfLen(), 5);
        seSlider.valueProperty().addListener((obs, o, n) -> {
            processor.setSeHalfLen(n.intValue());
            seLabel.setText(String.valueOf(n.intValue()));
        });
        HBox seRow = new HBox(8, seTitle, seSlider, seLabel);
        seRow.setAlignment(Pos.CENTER_LEFT);

        // --- bottom row: 4 morphological panels ---
        HBox morphRow = new HBox(8,
                panel("Open  —  (horiz)",    morphHView),
                panel("Open  |  (vert)",     morphVView),
                panel("Open  /  (diag fwd)", morphFwdView),
                panel("Open  \\  (diag bwd)", morphBwdView));
        morphRow.setAlignment(Pos.TOP_LEFT);
        morphRow.setPadding(new Insets(0));

        root.getChildren().addAll(topRow, binarizeBox, seRow, sectionLabel("Morphological Openings"), morphRow);
    }

    // -------------------------------------------------------------------------
    // update (FX thread)
    // -------------------------------------------------------------------------

    public void onResult(PreProcessingResult r) {
        if (r == null) return;

        binaryView  .setImage(r.binaryImage());
        morphHView  .setImage(r.morphHoriz());
        morphVView  .setImage(r.morphVert());
        morphFwdView.setImage(r.morphDiagFwd());
        morphBwdView.setImage(r.morphDiagBwd());

        drawHHistogram(r);
        drawVHistogram(r);
    }

    // -------------------------------------------------------------------------
    // histogram drawing
    // -------------------------------------------------------------------------

    private void drawHHistogram(PreProcessingResult r) {
        float[] sums = r.hRowSums();
        int h = r.frameHeight();
        int w = r.frameWidth();
        GraphicsContext gc = hHistCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, HIST_W, HIST_H);
        if (sums == null || sums.length == 0 || h == 0 || w == 0) return;

        double scaleY = HIST_H / (double) h;
        float maxV = 1f;
        for (float v : sums) if (v > maxV) maxV = v;

        for (int row = 0; row < sums.length; row++) {
            double y  = row * scaleY;
            double bh = Math.max(1.0, scaleY);
            double bw = (sums[row] / maxV) * (HIST_W - 2);
            gc.setFill(Color.rgb(0, 200, 100, 0.85));
            gc.fillRect(0, y, bw, bh);
        }
        // axis
        gc.setStroke(Color.rgb(180, 180, 180, 0.4));
        gc.setLineWidth(0.5);
        gc.strokeLine(0, 0, 0, HIST_H);
    }

    private void drawVHistogram(PreProcessingResult r) {
        float[] sums = r.vColSums();
        int w = r.frameWidth();
        int h = r.frameHeight();
        GraphicsContext gc = vHistCanvas.getGraphicsContext2D();
        gc.setFill(Color.rgb(20, 20, 20));
        gc.fillRect(0, 0, VHIST_W, VHIST_H);
        if (sums == null || sums.length == 0 || w == 0 || h == 0) return;

        double scaleX = VHIST_W / (double) w;
        float maxV = 1f;
        for (float v : sums) if (v > maxV) maxV = v;

        for (int col = 0; col < sums.length; col++) {
            double x  = col * scaleX;
            double bw = Math.max(1.0, scaleX);
            double bh = (sums[col] / maxV) * (VHIST_H - 2);
            gc.setFill(Color.rgb(100, 160, 255, 0.85));
            gc.fillRect(x, VHIST_H - bh, bw, bh);
        }
        // axis
        gc.setStroke(Color.rgb(180, 180, 180, 0.4));
        gc.setLineWidth(0.5);
        gc.strokeLine(0, VHIST_H - 1, VHIST_W, VHIST_H - 1);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static ImageView imageView(double w, double h) {
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setSmooth(false);
        return iv;
    }

    private VBox panel(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #bbbbbb;");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        frame.setPadding(new Insets(3));
        VBox p = new VBox(3, lbl, frame);
        p.setAlignment(Pos.TOP_LEFT);
        return p;
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

    private static Slider slider(double min, double max, double value, double tickUnit) {
        Slider s = new Slider(min, max, value);
        s.setShowTickLabels(true);
        s.setMajorTickUnit(tickUnit);
        s.setPrefWidth(200);
        return s;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
