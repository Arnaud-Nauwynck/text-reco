package fr.an.textreco;

import fr.an.textreco.processing.EdgeDetectorProcessor;
import fr.an.textreco.ui.CameraService;
import fr.an.textreco.ui.CameraView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.opencv.core.Core;

public class AppMain extends Application {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        CameraView view = new CameraView();
        EdgeDetectorProcessor processor = new EdgeDetectorProcessor();
        CameraService cameraService = new CameraService(processor);

        cameraService.getImageProperty().addListener((obs, oldV, newV) -> {
            view.setImage(newV);
        });

        stage.setScene(new Scene(view.getRoot(), 1000, 700));
        stage.setTitle("JavaFX OpenCV");

        stage.show();

        cameraService.start();

        stage.setOnCloseRequest(e -> { cameraService.stop(); });
    }

}