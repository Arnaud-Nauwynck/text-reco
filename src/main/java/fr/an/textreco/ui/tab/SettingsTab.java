package fr.an.textreco.ui.tab;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class SettingsTab {

    @Getter
    private final VBox root = new VBox(12);

    @Getter private final Spinner<Integer> cameraIndexSpinner   = new Spinner<>(0, 9, 0);
    @Getter private final Spinner<Integer> captureWidthSpinner  = new Spinner<>(320, 3840, 640, 160);
    @Getter private final Spinner<Integer> captureHeightSpinner = new Spinner<>(240, 2160, 480, 120);

    public SettingsTab(AppSettings appSettings, EdgeDetectorSettings edgeSettings,
                       PreProcessingProcessor preProcessor, TextLineExtractorProcessor lineExtractor) {
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color: #1e1e1e;");

        // --- Camera section ---
        cameraIndexSpinner.setEditable(true);
        captureWidthSpinner.setEditable(true);
        captureHeightSpinner.setEditable(true);

        GridPane cameraGrid = new GridPane();
        cameraGrid.setHgap(12);
        cameraGrid.setVgap(10);
        cameraGrid.add(styledLabel("Camera index:"),   0, 0); cameraGrid.add(cameraIndexSpinner,   1, 0);
        cameraGrid.add(styledLabel("Capture width:"),  0, 1); cameraGrid.add(captureWidthSpinner,  1, 1);
        cameraGrid.add(styledLabel("Capture height:"), 0, 2); cameraGrid.add(captureHeightSpinner, 1, 2);

        // --- Processing section ---
        CheckBox darkThemeBox = new CheckBox("Dark theme  (white text on black background)");
        darkThemeBox.setSelected(appSettings.isDarkTheme());
        darkThemeBox.setStyle("-fx-text-fill: #cccccc;");
        darkThemeBox.selectedProperty().addListener((obs, o, n) -> appSettings.setDarkTheme(n));

        // --- Pre-Processing section ---
        Label methodLabel = styledLabel("Binarization:");
        ComboBox<BinarizationMethod> methodCombo = new ComboBox<>(
                FXCollections.observableArrayList(BinarizationMethod.values()));
        methodCombo.setValue(preProcessor.getBinarizationMethod());
        methodCombo.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd;");

        Label thRadVal    = monoLabel(String.valueOf(preProcessor.getTophatRadius()));
        Slider thRadSlider = slider(1, 40, preProcessor.getTophatRadius(), 10);
        thRadSlider.valueProperty().addListener((obs, o, n) -> {
            preProcessor.setTophatRadius(n.intValue());
            thRadVal.setText(String.valueOf(n.intValue()));
        });

        Label thThrVal    = monoLabel(String.valueOf(preProcessor.getTophatThreshold()));
        Slider thThrSlider = slider(1, 100, preProcessor.getTophatThreshold(), 20);
        thThrSlider.valueProperty().addListener((obs, o, n) -> {
            preProcessor.setTophatThreshold(n.intValue());
            thThrVal.setText(String.valueOf(n.intValue()));
        });

        Label adaptBlockVal   = monoLabel(String.valueOf(preProcessor.getAdaptiveBlock()));
        Slider adaptBlockSlider = slider(3, 99, preProcessor.getAdaptiveBlock(), 20);
        adaptBlockSlider.valueProperty().addListener((obs, o, n) -> {
            preProcessor.setAdaptiveBlock(n.intValue());
            adaptBlockVal.setText(String.valueOf(n.intValue()));
        });

        VBox tophatParams = new VBox(4,
                hrow(styledLabel("Top-hat radius:"),    thRadSlider,      thRadVal),
                hrow(styledLabel("Top-hat threshold:"), thThrSlider,      thThrVal));
        VBox adaptiveParams = new VBox(4,
                hrow(styledLabel("Adaptive block:"),    adaptBlockSlider, adaptBlockVal));

        Runnable updateVisibility = () -> {
            BinarizationMethod m = methodCombo.getValue();
            tophatParams  .setVisible(m == BinarizationMethod.TOPHAT);
            tophatParams  .setManaged(m == BinarizationMethod.TOPHAT);
            adaptiveParams.setVisible(m == BinarizationMethod.ADAPTIVE);
            adaptiveParams.setManaged(m == BinarizationMethod.ADAPTIVE);
        };
        methodCombo.setOnAction(e -> {
            preProcessor.setBinarizationMethod(methodCombo.getValue());
            updateVisibility.run();
        });
        updateVisibility.run();

        Label seVal    = monoLabel(String.valueOf(preProcessor.getSeHalfLen()));
        Slider seSlider = slider(1, 20, preProcessor.getSeHalfLen(), 5);
        seSlider.valueProperty().addListener((obs, o, n) -> {
            preProcessor.setSeHalfLen(n.intValue());
            seVal.setText(String.valueOf(n.intValue()));
        });

        VBox preProcessingBox = new VBox(6,
                hrow(methodLabel, methodCombo),
                tophatParams,
                adaptiveParams,
                hrow(styledLabel("Morph SE half-length:"), seSlider, seVal));

        // --- Canny section ---
        Label cannyVal1 = monoLabel(String.valueOf((int) edgeSettings.getCannyThreshold1()));
        Slider cannySlider1 = slider(0, 300, edgeSettings.getCannyThreshold1(), 50);
        cannySlider1.valueProperty().addListener((obs, o, n) -> {
            edgeSettings.setCannyThreshold1(n.doubleValue());
            cannyVal1.setText(String.valueOf(n.intValue()));
        });

        Label cannyVal2 = monoLabel(String.valueOf((int) edgeSettings.getCannyThreshold2()));
        Slider cannySlider2 = slider(0, 500, edgeSettings.getCannyThreshold2(), 50);
        cannySlider2.valueProperty().addListener((obs, o, n) -> {
            edgeSettings.setCannyThreshold2(n.doubleValue());
            cannyVal2.setText(String.valueOf(n.intValue()));
        });

        VBox cannyBox = new VBox(6,
                hrow(styledLabel("Threshold 1 (low):"),  cannySlider1, cannyVal1),
                hrow(styledLabel("Threshold 2 (high):"), cannySlider2, cannyVal2));

        // --- Line Detection section ---
        Label smoothVal = monoLabel(String.valueOf(lineExtractor.getSmoothRadius()));
        Slider smoothSlider = slider(0, 15, lineExtractor.getSmoothRadius(), 5);
        smoothSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setSmoothRadius(n.intValue());
            smoothVal.setText(String.valueOf(n.intValue()));
        });

        Label valleyThrVal = monoLabel(String.format("%.2f", lineExtractor.getValleyThreshold()));
        Slider valleyThrSlider = slider(0.01, 0.5, lineExtractor.getValleyThreshold(), 0.1);
        valleyThrSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setValleyThreshold(n.doubleValue());
            valleyThrVal.setText(String.format("%.2f", n.doubleValue()));
        });

        Label valleyWinVal = monoLabel(String.valueOf(lineExtractor.getValleyHalfWin()));
        Slider valleyWinSlider = slider(1, 20, lineExtractor.getValleyHalfWin(), 5);
        valleyWinSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setValleyHalfWin(n.intValue());
            valleyWinVal.setText(String.valueOf(n.intValue()));
        });

        Label minPeakVal = monoLabel(String.format("%.2f", lineExtractor.getMinPeakRatio()));
        Slider minPeakSlider = slider(0.01, 0.3, lineExtractor.getMinPeakRatio(), 0.05);
        minPeakSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setMinPeakRatio(n.doubleValue());
            minPeakVal.setText(String.format("%.2f", n.doubleValue()));
        });

        Label minHeightVal = monoLabel(String.valueOf(lineExtractor.getMinLineHeight()));
        Slider minHeightSlider = slider(2, 30, lineExtractor.getMinLineHeight(), 5);
        minHeightSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setMinLineHeight(n.intValue());
            minHeightVal.setText(String.valueOf(n.intValue()));
        });

        Label maxHeightVal = monoLabel(String.valueOf(lineExtractor.getMaxLineHeight()));
        Slider maxHeightSlider = slider(20, 200, lineExtractor.getMaxLineHeight(), 20);
        maxHeightSlider.valueProperty().addListener((obs, o, n) -> {
            lineExtractor.setMaxLineHeight(n.intValue());
            maxHeightVal.setText(String.valueOf(n.intValue()));
        });

        VBox lineDetBox = new VBox(6,
                hrow(styledLabel("Smooth radius (rows):"),     smoothSlider,     smoothVal),
                hrow(styledLabel("Valley threshold:"),         valleyThrSlider,  valleyThrVal),
                hrow(styledLabel("Valley half-window (rows):"),valleyWinSlider,  valleyWinVal),
                hrow(styledLabel("Min peak ratio:"),           minPeakSlider,    minPeakVal),
                hrow(styledLabel("Min height (px):"),          minHeightSlider,  minHeightVal),
                hrow(styledLabel("Max height (px):"),          maxHeightSlider,  maxHeightVal));

        root.getChildren().addAll(
                sectionLabel("Camera"),             cameraGrid,
                new Separator(),
                sectionLabel("Processing"),         darkThemeBox,
                new Separator(),
                sectionLabel("Canny Edge Detection"), cannyBox,
                new Separator(),
                sectionLabel("Pre-Processing"),     preProcessingBox,
                new Separator(),
                sectionLabel("Line Detection"),     lineDetBox);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 14));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static Label monoLabel(String text) {
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
