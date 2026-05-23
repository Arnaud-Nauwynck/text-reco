package fr.an.textreco.processing;

import fr.an.textreco.model.FrameData;
import org.opencv.imgproc.Imgproc;

public class EdgeDetectorProcessor implements FrameProcessor {

    @Override
    public void process(
            FrameData frame,
            ProcessingContext ctx
    ) {

        Imgproc.cvtColor(frame.raw, frame.gray, Imgproc.COLOR_BGR2GRAY);

        Imgproc.Canny(frame.gray, frame.processed, 80, 150);

        Imgproc.cvtColor(frame.processed, frame.processed, Imgproc.COLOR_GRAY2BGR);
    }
}