package fr.an.textreco.ui;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.ui.tab.ColumnsDetectionView;
import fr.an.textreco.ui.tab.ImageInputView;
import fr.an.textreco.ui.tab.LineAreasDetectionView;
import fr.an.textreco.ui.tab.PerspectiveTransformView;
import fr.an.textreco.ui.tab.PreProcessingView;
import fr.an.textreco.ui.tab.ProcessingMonitoringView;
import fr.an.textreco.ui.tab.ResultTextView;
import fr.an.textreco.ui.tab.SettingsView;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class TextRecoView {

    @Getter
    private final BorderPane root = new BorderPane();

    public TextRecoView(ProcessingPipeline pipeline,
                        AppSettings appSettings,
                        EdgeDetectorSettings edgeSettings,
                        PerspectiveTransformProcessor perspectiveProcessor,
                        PreProcessingProcessor preProcessingProcessor,
                        TextLineExtractorProcessor lineExtractor) {

        TabPane tabPane = new TabPane(
                buildTab("Input",          new ImageInputView(pipeline).getRoot()),
                buildTab("Perspective",    new PerspectiveTransformView(pipeline, perspectiveProcessor).getRoot()),
                buildTab("Pre-Processing", new PreProcessingView(pipeline).getRoot()),
                buildTab("Line Areas",     new LineAreasDetectionView(pipeline, lineExtractor).getRoot()),
                buildTab("Columns",        new ColumnsDetectionView(pipeline).getRoot()),
                buildTab("Settings",       new SettingsView(appSettings, edgeSettings, preProcessingProcessor, lineExtractor).getRoot()),
                buildTab("Perfs",          new ProcessingMonitoringView(pipeline).getRoot()),
                buildTab("Results",        new ResultTextView().getRoot())
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
}
