package fr.an.textreco.ui;

import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import fr.an.textreco.ui.tab.CharClassifierView;
import fr.an.textreco.ui.tab.CharFeaturesView;
import fr.an.textreco.ui.tab.ImageInputView;
import fr.an.textreco.ui.tab.GridDetectView;
import fr.an.textreco.ui.tab.PerspectiveTransformView;
import fr.an.textreco.ui.tab.PreProcessingView;
import fr.an.textreco.ui.tab.ProcessingMonitoringView;
import fr.an.textreco.ui.tab.ResultTextView;
import fr.an.textreco.ui.tab.SettingsView;
import fr.an.textreco.ui.tab.TessOcrView;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

@Getter
public class TextRecoView {

    private final BorderPane root = new BorderPane();

    public TextRecoView(ProcessingContext context,
                        ProcessingPipeline pipeline,
                        PerspectiveTransformProcessor perspectiveProcessor,
                        TextLineExtractorProcessor lineExtractor) {

        TabPane tabPane = new TabPane(
                buildTab("Input",          new ImageInputView(context, pipeline).getRoot()),
                buildTab("Perspective",    new PerspectiveTransformView(context, perspectiveProcessor).getRoot()),
                buildTab("Pre-Processing", new PreProcessingView(context).getRoot()),
                buildTab("Grid Detect",    new GridDetectView(context, lineExtractor).getRoot()),
                buildTab("Char Classifier", new CharClassifierView(context, pipeline.getCharClassifier()).getRoot()),
                buildTab("Char Features",  new CharFeaturesView(pipeline.getCharTemplateDb()).getRoot()),
                buildTab("TessOCR",        new TessOcrView(context).getRoot()),
                buildTab("Settings",       new SettingsView(context, lineExtractor).getRoot()),
                buildTab("Perfs",          new ProcessingMonitoringView(context).getRoot()),
                buildTab("Results",        new ResultTextView(context).getRoot())
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
