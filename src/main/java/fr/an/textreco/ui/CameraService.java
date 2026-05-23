package fr.an.textreco.ui;

import fr.an.textreco.model.FrameData;
import fr.an.textreco.processing.FrameProcessor;
import fr.an.textreco.processing.ProcessingContext;
import fr.an.textreco.util.FxImageUtils;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;
import lombok.Getter;
import org.opencv.videoio.VideoCapture;

public class CameraService {

    @Getter
    private final ObjectProperty<Image> imageProperty = new SimpleObjectProperty<>();

    private final FrameProcessor processor;

    private volatile boolean running;

    private Thread worker;

    public CameraService(FrameProcessor processor) {
        this.processor = processor;
    }

    public void start() {
        running = true;
        worker = new Thread(this::runLoop);
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        try {
            worker.join();
        } catch (InterruptedException ignored) {
        }
    }


    private void runLoop() {
        VideoCapture capture = new VideoCapture(0);
        if (!capture.isOpened()) {
            System.err.println("Cannot open camera");
            return;
        }
        FrameData frame = new FrameData();
        ProcessingContext context = new ProcessingContext();

        while (running) {
            capture.read(frame.raw);
            if (frame.raw.empty()) {
                continue;
            }

            processor.process(frame, context);

            Image fxImage = FxImageUtils.matToJavaFXWritableImage(frame.processed);

            Platform.runLater(() -> {
                imageProperty.set(fxImage);
            });
        }

        capture.release();
        frame.release();
        context.release();
    }
}