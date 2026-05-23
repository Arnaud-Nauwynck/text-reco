package fr.an.textreco.ui;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.model.FrameStats;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.ui.tab.CameraTab;
import fr.an.textreco.ui.tab.PerspectiveTab;
import fr.an.textreco.ui.tab.PreProcessingTab;
import fr.an.textreco.ui.tab.ProcessingTab;
import fr.an.textreco.ui.tab.ResultsTab;
import fr.an.textreco.ui.tab.SettingsTab;
import fr.an.textreco.ui.tab.TextLinesTab;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class TextRecoView {

    @Getter
    private final BorderPane root = new BorderPane();

    private final CameraTab cameraTab;
    private final ProcessingTab processingTab;
    private final PerspectiveTab perspectiveTab;
    private final PreProcessingTab preProcessingTab;
    private final TextLinesTab textLinesTab;
    private final SettingsTab settingsTab;
    private final ResultsTab resultsTab;

    public TextRecoView(CameraService cameraService,
                        AppSettings appSettings,
                        EdgeDetectorSettings edgeSettings,
                        EdgeDetectorProcessor processor,
                        PerspectiveTransformProcessor perspectiveProcessor,
                        PreProcessingProcessor preProcessingProcessor,
                        TextLineExtractorProcessor lineExtractor) {
        cameraTab        = new CameraTab(cameraService);
        processingTab    = new ProcessingTab(edgeSettings);
        perspectiveTab   = new PerspectiveTab(perspectiveProcessor);
        preProcessingTab = new PreProcessingTab(preProcessingProcessor);
        textLinesTab     = new TextLinesTab(lineExtractor);
        settingsTab      = new SettingsTab(appSettings);
        resultsTab       = new ResultsTab();

        TabPane tabPane = new TabPane(
                buildTab("Input",          cameraTab.getRoot()),
                buildTab("Perfs",          processingTab.getRoot()),
                buildTab("Perspective",    perspectiveTab.getRoot()),
                buildTab("Pre-Processing", preProcessingTab.getRoot()),
                buildTab("Text Lines",     textLinesTab.getRoot()),
                buildTab("Settings",       settingsTab.getRoot()),
                buildTab("Results",        resultsTab.getRoot())
        );
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #1e1e1e;");

        root.setCenter(tabPane);
        root.setStyle("-fx-background-color: #1e1e1e;");
    }

    private Tab buildTab(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        label.setStyle("-fx-text-fill: #dddddd;");
        Tab tab = new Tab(null, content);
        tab.setGraphic(label);
        tab.setStyle("-fx-background-color: #2d2d2d;");
        return tab;
    }

    public void setRawImage(Image image) {
        cameraTab.setRawImage(image);
        perspectiveTab.setRawImage(image);
    }

    public void setProcessedImage(Image image) {
        cameraTab.setProcessedImage(image);
    }

    public void setPerspectiveImage(Image image) {
        perspectiveTab.setWarpedImage(image);
        textLinesTab.setWarpedImage(image);
    }

    public void onPreProcessing(PreProcessingResult result) {
        preProcessingTab.onResult(result);
    }

    public void onTextLines(TextLineExtractionResult result) {
        textLinesTab.onResult(result);
    }

    public void onStats(FrameStats stats) {
        processingTab.onStats(stats);
    }
}
