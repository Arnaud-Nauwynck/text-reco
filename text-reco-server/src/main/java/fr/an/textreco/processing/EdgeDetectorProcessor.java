package fr.an.textreco.processing;

import fr.an.textreco.model.EdgeDetectorSettings;
import fr.an.textreco.model.FrameData;
import lombok.Getter;
import org.opencv.imgproc.Imgproc;

public class EdgeDetectorProcessor implements FrameProcessor {

    @Getter
    private final EdgeDetectorSettings settings;

    private final FrameData frame = new FrameData();
    private final ScratchContext ctx = new ScratchContext();

    public EdgeDetectorProcessor(EdgeDetectorSettings settings) {
        this.settings = settings;
    }

    public void process(org.opencv.core.Mat rawBgr) {
        rawBgr.copyTo(frame.raw);
        Imgproc.cvtColor(frame.raw, frame.gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(frame.gray, frame.processed,
                settings.getCannyThreshold1(), settings.getCannyThreshold2());
        Imgproc.cvtColor(frame.processed, frame.processed, Imgproc.COLOR_GRAY2BGR);
    }

    public org.opencv.core.Mat getProcessed() {
        return frame.processed;
    }

    @Override
    public void process(FrameData f, ScratchContext c) {
        Imgproc.cvtColor(f.raw, f.gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(f.gray, f.processed,
                settings.getCannyThreshold1(), settings.getCannyThreshold2());
        Imgproc.cvtColor(f.processed, f.processed, Imgproc.COLOR_GRAY2BGR);
    }

    public void release() {
        frame.release();
        ctx.release();
    }
}
