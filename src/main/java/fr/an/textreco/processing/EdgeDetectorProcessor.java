package fr.an.textreco.processing;

import fr.an.textreco.model.FrameData;
import lombok.Getter;
import lombok.Setter;
import org.opencv.imgproc.Imgproc;

public class EdgeDetectorProcessor implements FrameProcessor {

    @Getter @Setter private volatile double cannyThreshold1 = 80;
    @Getter @Setter private volatile double cannyThreshold2 = 150;

    @Override
    public void process(FrameData frame, ProcessingContext ctx) {
        Imgproc.cvtColor(frame.raw, frame.gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(frame.gray, frame.processed, cannyThreshold1, cannyThreshold2);
        Imgproc.cvtColor(frame.processed, frame.processed, Imgproc.COLOR_GRAY2BGR);
    }
}