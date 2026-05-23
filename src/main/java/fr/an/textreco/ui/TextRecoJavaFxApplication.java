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

import java.io.File;

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
        AppSettings          appSettings   = new AppSettings();
        EdgeDetectorSettings edgeSettings  = new EdgeDetectorSettings();

        EdgeDetectorProcessor edgeDetector   = new EdgeDetectorProcessor(edgeSettings);
        PerspectiveTransformProcessor perspProcessor = new PerspectiveTransformProcessor();
        PreProcessingProcessor preProcessor   = new PreProcessingProcessor(appSettings);
        TextLineExtractorProcessor    lineExtractor  = new TextLineExtractorProcessor();

        ProcessingPipeline pipeline = new ProcessingPipeline(
                edgeDetector, perspProcessor, preProcessor, lineExtractor);

        File file = new File("src/test/term36-consolas-base64-bis.png");
        if (file.exists()) {
            pipeline.loadImageFile(file);
        }

        // Views subscribe to pipeline properties in their own constructors.
        TextRecoView view = new TextRecoView(
                pipeline, appSettings, edgeSettings,
                perspProcessor, preProcessor, lineExtractor);

        stage.getIcons().clear();
        stage.setScene(new Scene(view.getRoot(), 1400, 700));
        stage.setTitle("TextReco");
        stage.show();

        pipeline.start();

        stage.setOnCloseRequest(e -> pipeline.stop());
    }
}
