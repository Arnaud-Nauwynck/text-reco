package fr.an.textreco.ui;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.processing.PreProcessingProcessor;
import fr.an.textreco.processing.TextLineExtractorProcessor;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import nu.pattern.OpenCV;

public class TextRecoJavaFxApplication extends Application {

    static {
        try {
            OpenCV.loadLocally();
        } catch (Error ex) {
            System.out.println("Failed to load native OpenCV lib ...");
            ex.printStackTrace();
            throw ex;
        }
    }

    @Override
    public void start(Stage stage) {
        AppSettings appSettings       = new AppSettings();
        EdgeDetectorSettings edgeSettings = new EdgeDetectorSettings();

        EdgeDetectorProcessor         edgeDetector    = new EdgeDetectorProcessor(edgeSettings);
        PerspectiveTransformProcessor perspProcessor  = new PerspectiveTransformProcessor();
        PreProcessingProcessor        preProcessor    = new PreProcessingProcessor(appSettings);
        TextLineExtractorProcessor    lineExtractor   = new TextLineExtractorProcessor();

        ProcessingPipeline pipeline = new ProcessingPipeline(
                edgeDetector, perspProcessor, preProcessor, lineExtractor);

        TextRecoView view = new TextRecoView(
                pipeline, appSettings, edgeSettings,
                perspProcessor, preProcessor, lineExtractor);

        pipeline.getRawImageProperty()        .addListener((obs, o, n) -> view.setRawImage(n));
        pipeline.getPerspectiveImageProperty().addListener((obs, o, n) -> view.setPerspectiveImage(n));
        pipeline.getPreProcessingProperty()   .addListener((obs, o, n) -> view.onPreProcessing(n));
        pipeline.getTextLinesProperty()       .addListener((obs, o, n) -> view.onTextLines(n));
        pipeline.getFrameStatsProperty()      .addListener((obs, o, n) -> view.onStats(n));

        stage.getIcons().clear();
        stage.setScene(new Scene(view.getRoot(), 1100, 620));
        stage.setTitle("TextReco");
        stage.show();

        pipeline.start();

        stage.setOnCloseRequest(e -> pipeline.stop());
    }
}
