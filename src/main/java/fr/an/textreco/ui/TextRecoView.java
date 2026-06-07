package fr.an.textreco.ui;

import fr.an.textreco.model.ProcessingContext;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.ui.tab.CharClassifierView;
import fr.an.textreco.ui.tab.CharFeaturesView;
import fr.an.textreco.ui.tab.ImageInputView;
import fr.an.textreco.ui.tab.GridDetectView;
import fr.an.textreco.ui.tab.LineHeightView;
import fr.an.textreco.ui.tab.CharWidthView;
import fr.an.textreco.ui.tab.LineOffsetView;
import fr.an.textreco.ui.tab.ColumnOffsetView;
import fr.an.textreco.ui.tab.PerspectiveTransformView;
import fr.an.textreco.ui.tab.PreProcessingView;
import fr.an.textreco.ui.tab.ProcessingMonitoringView;
import fr.an.textreco.ui.tab.ResultTextView;
import fr.an.textreco.ui.tab.SettingsView;
import fr.an.textreco.ui.tab.TessOcrView;
import fr.an.textreco.ui.viewmodel.CharClassifierViewModel;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import fr.an.textreco.ui.viewmodel.ImageInputViewModel;
import fr.an.textreco.ui.viewmodel.PerspectiveTransformViewModel;
import fr.an.textreco.ui.viewmodel.PreProcessingViewModel;
import fr.an.textreco.ui.viewmodel.ProcessingMonitoringViewModel;
import fr.an.textreco.ui.viewmodel.ResultTextViewModel;
import fr.an.textreco.ui.viewmodel.SettingsViewModel;
import fr.an.textreco.ui.viewmodel.TessOcrViewModel;
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
                        PerspectiveTransformProcessor perspectiveProcessor) {

        // --- Construct ViewModels ---
        ImageInputViewModel imageInputVM = new ImageInputViewModel(context, pipeline);
        PerspectiveTransformViewModel perspectiveVM = new PerspectiveTransformViewModel(context, perspectiveProcessor);
        PreProcessingViewModel preProcessingVM = new PreProcessingViewModel(context);
        GridDetectViewModel gridDetectVM = new GridDetectViewModel(context);
        CharClassifierViewModel charClassifierVM = new CharClassifierViewModel(context, pipeline.getCharClassifier());
        TessOcrViewModel tessOcrVM = new TessOcrViewModel(context);
        SettingsViewModel settingsVM = new SettingsViewModel(context);
        ProcessingMonitoringViewModel monitoringVM = new ProcessingMonitoringViewModel(context);
        ResultTextViewModel resultTextVM = new ResultTextViewModel(context);

        // --- Construct Views with their ViewModels ---
        TabPane tabPane = new TabPane(
                buildTab("Input", new ImageInputView(imageInputVM).getRoot()),
                buildTab("Perspective", new PerspectiveTransformView(perspectiveVM).getRoot()),
                buildTab("Pre-Processing", new PreProcessingView(preProcessingVM).getRoot()),
                buildTab("Grid Detect", new GridDetectView(gridDetectVM).getRoot()),
                buildTab("Line Height", new LineHeightView(gridDetectVM).getRoot()),
                buildTab("Char Width", new CharWidthView(gridDetectVM).getRoot()),
                buildTab("Line Offset", new LineOffsetView(gridDetectVM).getRoot()),
                buildTab("Column Offset", new ColumnOffsetView(gridDetectVM).getRoot()),
                buildTab("Char Classifier", new CharClassifierView(charClassifierVM).getRoot()),
                buildTab("Char Features", new CharFeaturesView(pipeline.getCharTemplateDb()).getRoot()),
                buildTab("TessOCR", new TessOcrView(tessOcrVM).getRoot()),
                buildTab("Settings", new SettingsView(settingsVM).getRoot()),
                buildTab("Perfs", new ProcessingMonitoringView(monitoringVM).getRoot()),
                buildTab("Results", new ResultTextView(resultTextVM).getRoot())
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
