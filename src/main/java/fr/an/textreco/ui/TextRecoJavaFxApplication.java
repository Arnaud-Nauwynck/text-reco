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
        } catch(Error ex) {
            System.out.println("Failed to load native OpenCV lib ...");
            ex.printStackTrace();
            throw ex;
        }
        // System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void start(Stage stage) {
        AppSettings appSettings = new AppSettings();
        EdgeDetectorSettings edgeSettings = new EdgeDetectorSettings();
        EdgeDetectorProcessor processor = new EdgeDetectorProcessor(edgeSettings);
        PerspectiveTransformProcessor perspectiveProcessor = new PerspectiveTransformProcessor();
        PreProcessingProcessor preProcessingProcessor = new PreProcessingProcessor(appSettings);
        TextLineExtractorProcessor lineExtractor = new TextLineExtractorProcessor(appSettings);

        CameraService cameraService = new CameraService(
                processor, perspectiveProcessor, preProcessingProcessor, lineExtractor);

        TextRecoView view = new TextRecoView(
                cameraService, appSettings, edgeSettings,
                processor, perspectiveProcessor, preProcessingProcessor, lineExtractor);

        cameraService.getRawImageProperty()        .addListener((obs, o, n) -> view.setRawImage(n));
        cameraService.getProcessedImageProperty()  .addListener((obs, o, n) -> view.setProcessedImage(n));
        cameraService.getPerspectiveImageProperty().addListener((obs, o, n) -> view.setPerspectiveImage(n));
        cameraService.getPreProcessingProperty()   .addListener((obs, o, n) -> view.onPreProcessing(n));
        cameraService.getTextLinesProperty()       .addListener((obs, o, n) -> view.onTextLines(n));
        cameraService.getFrameStatsProperty()      .addListener((obs, o, n) -> view.onStats(n));

        stage.getIcons().clear();
        stage.setScene(new Scene(view.getRoot(), 1100, 620));
        stage.setTitle("TextReco");

        stage.show();

        cameraService.start();

        stage.setOnCloseRequest(e -> cameraService.stop());
    }
}
