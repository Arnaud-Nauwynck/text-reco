package fr.an.textreco.ui;

import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.ui.tab.CameraTab;
import fr.an.textreco.ui.tab.ProcessingTab;
import fr.an.textreco.ui.tab.ResultsTab;
import fr.an.textreco.ui.tab.SettingsTab;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import lombok.Getter;

public class TextRecoView {

    @Getter
    private final BorderPane root = new BorderPane();

    private final CameraTab cameraTab;
    private final ProcessingTab processingTab;
    private final SettingsTab settingsTab;
    private final ResultsTab resultsTab;

    public TextRecoView(EdgeDetectorProcessor processor) {
        cameraTab = new CameraTab();
        processingTab = new ProcessingTab(processor);
        settingsTab = new SettingsTab();
        resultsTab = new ResultsTab();

        TabPane tabPane = new TabPane(
                buildTab("Camera",     cameraTab.getRoot()),
                buildTab("Processing", processingTab.getRoot()),
                buildTab("Settings",   settingsTab.getRoot()),
                buildTab("Results",    resultsTab.getRoot())
        );
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #1e1e1e;");

        root.setCenter(tabPane);
        root.setStyle("-fx-background-color: #1e1e1e;");
    }

    private Tab buildTab(String title, javafx.scene.Node content) {
        Tab tab = new Tab(title, content);
        tab.setStyle("-fx-background-color: #2d2d2d; -fx-text-fill: #dddddd;");
        return tab;
    }

    public void setRawImage(Image image) {
        cameraTab.setRawImage(image);
        if (image != null) {
            processingTab.onFrame((int) image.getWidth(), (int) image.getHeight());
        }
    }

    public void setProcessedImage(Image image) {
        cameraTab.setProcessedImage(image);
    }
}
