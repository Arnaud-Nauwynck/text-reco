package fr.an.textreco.processing;

import fr.an.textreco.model.FrameData;
import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class PerspectiveTransformProcessor implements FrameProcessor {

    // owned scratch state
    private final FrameData frame = new FrameData();
    private final ScratchContext ctx = new ScratchContext();

    private volatile int outputWidth = 640;
    private volatile int outputHeight = 480;

    // source corners in image-relative coordinates [0..1], order: TL, TR, BR, BL
    private volatile double[] srcXRel = {0.0, 1.0, 1.0, 0.0};
    private volatile double[] srcYRel = {0.0, 0.0, 1.0, 1.0};

    // pre-allocated per-frame scratch — only reallocated when output size changes
    private final Point[] srcPts = new Point[]{new Point(), new Point(), new Point(), new Point()};
    private final Point[] dstPts = new Point[]{new Point(), new Point(), new Point(), new Point()};
    private final MatOfPoint2f srcMat = MatFacade.allocMatOfPoint2f("Perspective.srcMat");
    private final MatOfPoint2f dstMat = MatFacade.allocMatOfPoint2f("Perspective.dstMat");
    private final Mat transform = MatFacade.alloc("Perspective.transform");
    private int lastOW = -1;
    private int lastOH = -1;
    // cache the inputs the transform depends on, so getPerspectiveTransform
    // (which allocates a fresh Mat each call) only runs when something changed
    private final double[] lastSrcX = new double[4];
    private final double[] lastSrcY = new double[4];
    private boolean transformValid = false;
    private final Size outSize = new Size();

    public void setOutputSize(int w, int h) {
        outputWidth = w;
        outputHeight = h;
    }

    public void setCorners(double[] xRel, double[] yRel) {
        this.srcXRel = xRel.clone();
        this.srcYRel = yRel.clone();
    }

    public double[] getSrcXRel() {
        return srcXRel.clone();
    }

    public double[] getSrcYRel() {
        return srcYRel.clone();
    }

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
        boolean sizeChanged = (ow != lastOW || oh != lastOH);
        if (sizeChanged) {
            dstPts[0].x = 0;
            dstPts[0].y = 0;
            dstPts[1].x = ow - 1;
            dstPts[1].y = 0;
            dstPts[2].x = ow - 1;
            dstPts[2].y = oh - 1;
            dstPts[3].x = 0;
            dstPts[3].y = oh - 1;
            dstMat.fromArray(dstPts);
            lastOW = ow;
            lastOH = oh;
            outSize.width = ow;
            outSize.height = oh;
        }

        // Only recompute the perspective transform when its inputs actually change.
        // getPerspectiveTransform allocates a fresh 3×3 Mat internally on every call,
        // so caching avoids a per-frame native allocation.
        boolean srcChanged = !transformValid;
        for (int i = 0; i < 4 && !srcChanged; i++) {
            if (srcPts[i].x != lastSrcX[i] || srcPts[i].y != lastSrcY[i]) srcChanged = true;
        }
        if (sizeChanged || srcChanged) {
            srcMat.fromArray(srcPts);
            Mat t = MatFacade.adopt(Imgproc.getPerspectiveTransform(srcMat, dstMat),
                    "Perspective.getPerspectiveTransform");
            t.copyTo(transform);
            MatFacade.release(t, "Perspective.getPerspectiveTransform");
            for (int i = 0; i < 4; i++) {
                lastSrcX[i] = srcPts[i].x;
                lastSrcY[i] = srcPts[i].y;
            }
            transformValid = true;
        }

        Imgproc.warpPerspective(frame.raw, frame.processed, transform, outSize);
    }

    /**
     * Runs perspective warp on rawBgr. Result is in {@link #getWarped()}.
     */
    public void process(Mat rawBgr) {
        rawBgr.copyTo(frame.raw);
        process(frame, ctx);
    }

    /**
     * Returns the warped output Mat (valid until the next process() call).
     */
    public Mat getWarped() {
        return frame.processed;
    }

    public void release() {
        MatFacade.release(transform, "Perspective.transform");
        MatFacade.release(srcMat, "Perspective.srcMat");
        MatFacade.release(dstMat, "Perspective.dstMat");
        frame.release();
        ctx.release();
    }
}
