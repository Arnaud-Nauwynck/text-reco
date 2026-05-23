package fr.an.textreco.ui;

import fr.an.textreco.processing.EdgeDetectorProcessor;
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
        TextRecoView view = new TextRecoView();
        EdgeDetectorProcessor processor = new EdgeDetectorProcessor();

        CameraService cameraService = new CameraService(processor);

        cameraService.getRawImageProperty().addListener((obs, oldV, newV) -> view.setRawImage(newV));
        cameraService.getProcessedImageProperty().addListener((obs, oldV, newV) -> view.setProcessedImage(newV));

        stage.getIcons().clear();
        stage.setScene(new Scene(view.getRoot(), 1020, 480));
        stage.setTitle("TextReco");
        
        stage.show();

        cameraService.start();

        stage.setOnCloseRequest(e -> { cameraService.stop(); });
    }

}