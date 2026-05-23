package fr.an.textreco.processing;

import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.model.FrameData;
import lombok.Getter;
import org.opencv.imgproc.Imgproc;

public class EdgeDetectorProcessor implements FrameProcessor {

    @Getter
    private final EdgeDetectorSettings settings;

    public EdgeDetectorProcessor(EdgeDetectorSettings settings) {
        this.settings = settings;
    }

    @Override
    public void process(FrameData frame, ProcessingContext ctx) {
        Imgproc.cvtColor(frame.raw, frame.gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(frame.gray, frame.processed, settings.getCannyThreshold1(), settings.getCannyThreshold2());
        Imgproc.cvtColor(frame.processed, frame.processed, Imgproc.COLOR_GRAY2BGR);
    }
}
