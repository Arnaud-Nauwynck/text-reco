package fr.an.textreco.ui.tab;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class SettingsView {

    @Getter
    private final TabPane root = new TabPane();

    @Getter private final Spinner<Integer> cameraIndexSpinner   = new Spinner<>(0, 9, 0);
    @Getter private final Spinner<Integer> captureWidthSpinner  = new Spinner<>(320, 3840, 640, 160);
    @Getter private final Spinner<Integer> captureHeightSpinner = new Spinner<>(240, 2160, 480, 120);

    public SettingsView(AppSettings appSettings, EdgeDetectorSettings edgeSettings,
                        PreProcessingProcessor preProcessor, TextLineExtractorProcessor lineExtractor) {

        root.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.setStyle("-fx-background-color: #1e1e1e;");
        root.setTabMinWidth(90);

        root.getTabs().addAll(
                buildCameraTab(),
                buildProcessingTab(appSettings),
                buildCannyTab(edgeSettings),
                buildPreProcessingTab(preProcessor),
                buildLineDetectionTab(lineExtractor));
    }

    // -------------------------------------------------------------------------
    // sub-tab builders
    // -------------------------------------------------------------------------

    private Tab buildCameraTab() {
        cameraIndexSpinner.setEditable(true);
        captureWidthSpinner.setEditable(true);
        captureHeightSpinner.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(styledLabel("Camera index:"),   0, 0); grid.add(cameraIndexSpinner,   1, 0);
        grid.add(styledLabel("Capture width:"),  0, 1); grid.add(captureWidthSpinner,  1, 1);
        grid.add(styledLabel("Capture height:"), 0, 2); grid.add(captureHeightSpinner, 1, 2);

        return subTab("Camera", padded(grid));
    }

    private Tab buildProcessingTab(AppSettings appSettings) {
        CheckBox darkThemeBox = new CheckBox("Dark theme  (white text on black background)");
        darkThemeBox.setStyle("-fx-text-fill: #cccccc;");
        darkThemeBox.selectedProperty().bindBidirectional(appSettings.darkThemeProperty());
        return subTab("Processing", padded(darkThemeBox));
    }

    private Tab buildCannyTab(EdgeDetectorSettings edgeSettings) {
        Label val1 = monoLabel("0");
        Slider s1  = slider(0, 300, 50);
        s1.valueProperty().bindBidirectional(edgeSettings.cannyThreshold1Property());
        val1.textProperty().bind(Bindings.format("%.0f", edgeSettings.cannyThreshold1Property()));

        Label val2 = monoLabel("0");
        Slider s2  = slider(0, 500, 50);
        s2.valueProperty().bindBidirectional(edgeSettings.cannyThreshold2Property());
        val2.textProperty().bind(Bindings.format("%.0f", edgeSettings.cannyThreshold2Property()));

        VBox box = new VBox(6,
                hrow(styledLabel("Threshold 1 (low):"),  s1, val1),
                hrow(styledLabel("Threshold 2 (high):"), s2, val2));
        return subTab("Canny", padded(box));
    }

    private Tab buildPreProcessingTab(PreProcessingProcessor preProcessor) {
        ComboBox<BinarizationMethod> methodCombo = new ComboBox<>(
                FXCollections.observableArrayList(BinarizationMethod.values()));
        methodCombo.setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #dddddd;");
        methodCombo.valueProperty().bindBidirectional(preProcessor.binarizationMethodProperty());

        Label thRadVal = monoLabel("0");
        Slider thRadSlider = slider(1, 40, 10);
        bindIntSlider(thRadSlider, thRadVal, preProcessor.tophatRadiusProperty());

        Label thThrVal = monoLabel("0");
        Slider thThrSlider = slider(1, 100, 20);
        bindIntSlider(thThrSlider, thThrVal, preProcessor.tophatThresholdProperty());

        Label adaptBlockVal = monoLabel("0");
        Slider adaptBlockSlider = slider(3, 99, 20);
        bindIntSlider(adaptBlockSlider, adaptBlockVal, preProcessor.adaptiveBlockProperty());

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
        preProcessor.binarizationMethodProperty().addListener((obs, o, n) -> updateVisibility.run());
        updateVisibility.run();

        Label seVal = monoLabel("0");
        Slider seSlider = slider(1, 20, 5);
        bindIntSlider(seSlider, seVal, preProcessor.seHalfLenProperty());

        VBox box = new VBox(6,
                hrow(styledLabel("Binarization:"), methodCombo),
                tophatParams,
                adaptiveParams,
                hrow(styledLabel("Morph SE half-length:"), seSlider, seVal));
        return subTab("Pre-Processing", padded(box));
    }

    private Tab buildLineDetectionTab(TextLineExtractorProcessor lineExtractor) {
        Label info = styledLabel("Lines are computed from the grid: y0 + N × lineH (see Line Areas tab).");
        info.setWrapText(true);
        return subTab("Line Detection", padded(info));
    }

    // -------------------------------------------------------------------------
    // binding helpers
    // -------------------------------------------------------------------------

    private static void bindIntSlider(Slider slider, Label label, IntegerProperty prop) {
        slider.valueProperty().addListener((obs, o, n) -> prop.set(n.intValue()));
        prop.addListener((obs, o, n) -> slider.setValue(n.intValue()));
        slider.setValue(prop.get());
        label.textProperty().bind(prop.asString());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Tab subTab(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        lbl.setStyle("-fx-text-fill: #dddddd;");
        Tab tab = new Tab(null, content);
        tab.setGraphic(lbl);
        tab.setClosable(false);
        tab.setStyle("-fx-background-color: #2d2d2d;");
        return tab;
    }

    private static VBox padded(javafx.scene.Node content) {
        VBox box = new VBox(content);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #1e1e1e;");
        return box;
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

    private static Slider slider(double min, double max, double tickUnit) {
        Slider s = new Slider(min, max, min);
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
