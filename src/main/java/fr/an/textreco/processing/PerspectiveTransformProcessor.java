package fr.an.textreco.processing;

import fr.an.textreco.model.FrameData;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class PerspectiveTransformProcessor implements FrameProcessor {

    // owned scratch state
    private final FrameData      frame = new FrameData();
    private final ScratchContext ctx   = new ScratchContext();

    private volatile int outputWidth  = 640;
    private volatile int outputHeight = 480;

    // source corners in image-relative coordinates [0..1], order: TL, TR, BR, BL
    private volatile double[] srcXRel = {0.0, 1.0, 1.0, 0.0};
    private volatile double[] srcYRel = {0.0, 0.0, 1.0, 1.0};

    // pre-allocated per-frame scratch — only reallocated when output size changes
    private final Point[]       srcPts    = new Point[]{new Point(), new Point(), new Point(), new Point()};
    private final Point[]       dstPts    = new Point[]{new Point(), new Point(), new Point(), new Point()};
    private final MatOfPoint2f  srcMat    = new MatOfPoint2f();
    private final MatOfPoint2f  dstMat    = new MatOfPoint2f();
    private final Mat           transform = new Mat();
    private int lastOW = -1;
    private int lastOH = -1;

    public void setOutputSize(int w, int h) {
        outputWidth  = w;
        outputHeight = h;
    }

    public void setCorners(double[] xRel, double[] yRel) {
        this.srcXRel = xRel.clone();
        this.srcYRel = yRel.clone();
    }

    public double[] getSrcXRel() { return srcXRel.clone(); }
    public double[] getSrcYRel() { return srcYRel.clone(); }

    @Override
    public void process(FrameData frame, ScratchContext ctx) {
        int w = frame.raw.cols();
        int h = frame.raw.rows();
        if (w == 0 || h == 0) return;

        double[] xr = srcXRel;
        double[] yr = srcYRel;
        for (int i = 0; i < 4; i++) {
            srcPts[i].x = xr[i] * w;
            srcPts[i].y = yr[i] * h;
        }

        int ow = outputWidth;
        int oh = outputHeight;
        if (ow != lastOW || oh != lastOH) {
            dstPts[0].x = 0;      dstPts[0].y = 0;
            dstPts[1].x = ow - 1; dstPts[1].y = 0;
            dstPts[2].x = ow - 1; dstPts[2].y = oh - 1;
            dstPts[3].x = 0;      dstPts[3].y = oh - 1;
            dstMat.fromArray(dstPts);
            lastOW = ow;
            lastOH = oh;
        }

        srcMat.fromArray(srcPts);
        // getPerspectiveTransform writes into its return value; pass transform directly as dst
        Mat t = Imgproc.getPerspectiveTransform(srcMat, dstMat);
        t.copyTo(transform);
        t.release();
        Imgproc.warpPerspective(frame.raw, frame.processed, transform, new Size(ow, oh));
    }

    /** Runs perspective warp on rawBgr. Result is in {@link #getWarped()}. */
    public void process(Mat rawBgr) {
        rawBgr.copyTo(frame.raw);
        process(frame, ctx);
    }

    /** Returns the warped output Mat (valid until the next process() call). */
    public Mat getWarped() { return frame.processed; }

    public void release() {
        transform.release();
        srcMat.release();
        dstMat.release();
        frame.release();
        ctx.release();
    }
}
