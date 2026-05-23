package fr.an.textreco.processing;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.util.FxImageUtils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Computes all pre-processing intermediates from a perspective-corrected BGR frame:
 *   - binarisation (top-hat, adaptive, or Otsu)
 *   - horizontal and vertical projection histograms
 *   - morphological opening with 4 line structuring elements: — | / \
 *
 * All Mats are pre-allocated; float[] buffers are reallocated only on dimension change.
 */
public class PreProcessingProcessor {

    // binarisation
    private volatile BinarizationMethod binarizationMethod = BinarizationMethod.TOPHAT;
    /** top-hat SE radius (square) */
    private volatile int  tophatRadius    = 12;
    /** fixed threshold applied after top-hat */
    private volatile int  tophatThreshold = 20;
    /** adaptiveThreshold block size (must be odd) */
    private volatile int  adaptiveBlock   = 31;
    /** adaptiveThreshold C constant */
    private volatile int  adaptiveC       = 10;

    /** half-length of the morphological-opening line SE in pixels */
    private volatile int seHalfLen = 7;

    private final AppSettings appSettings;

    // scratch Mats — binarisation pipeline
    private final Mat gray      = new Mat();
    private final Mat tophatSE  = new Mat();   // rebuilt when tophatRadius changes
    private final Mat tophat    = new Mat();
    private final Mat binary    = new Mat();

    private int lastTophatRadius = -1;

    // scratch Mats — projection + morph
    private final Mat rowSumMat   = new Mat();
    private final Mat colSumMat   = new Mat();
    // persistent morph results — readable by TextLineExtractorProcessor after process() returns
    public final Mat morphHorizMat  = new Mat();
    public final Mat morphVertMat   = new Mat();
    private final Mat morphOut    = new Mat();   // scratch for diag openings

    // structuring elements — rebuilt when seHalfLen changes
    private int lastSeHalfLen = -1;
    private Mat seHoriz    = new Mat();
    private Mat seVert     = new Mat();
    private Mat seDiagFwd  = new Mat();
    private Mat seDiagBwd  = new Mat();

    // projection buffers
    private float[] hRowSums = new float[0];
    private float[] vColSums = new float[0];

    // ImageBuffers for FX conversion — one per output image
    private final FxImageUtils.ImageBuffer binaryBuf   = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphHBuf   = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphVBuf   = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphFwdBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphBwdBuf = new FxImageUtils.ImageBuffer();

    // scratch BGR wrapper for single-channel → BGR conversion before ImageBuffer
    private final Mat bgrTmp = new Mat();

    public PreProcessingProcessor(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    public void setSeHalfLen(int halfLen)                  { seHalfLen = halfLen; }
    public int  getSeHalfLen()                             { return seHalfLen; }
    public void setBinarizationMethod(BinarizationMethod m){ binarizationMethod = m; }
    public BinarizationMethod getBinarizationMethod()      { return binarizationMethod; }
    public void setTophatRadius(int r)                     { tophatRadius = r; }
    public int  getTophatRadius()                          { return tophatRadius; }
    public void setTophatThreshold(int t)                  { tophatThreshold = t; }
    public int  getTophatThreshold()                       { return tophatThreshold; }
    public void setAdaptiveBlock(int b)                    { adaptiveBlock = b; }
    public int  getAdaptiveBlock()                         { return adaptiveBlock; }
    public void setAdaptiveC(int c)                        { adaptiveC = c; }
    public int  getAdaptiveC()                             { return adaptiveC; }

    public PreProcessingResult process(Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0) return null;

        Imgproc.cvtColor(warpedBgr, gray, Imgproc.COLOR_BGR2GRAY);

        switch (binarizationMethod) {
            case TOPHAT   -> binarizeTophat();
            case ADAPTIVE -> binarizeAdaptive();
            case OTSU     -> binarizeOtsu();
        }

        // binary → BGR for ImageBuffer (expects 3-channel)
        Imgproc.cvtColor(binary, bgrTmp, Imgproc.COLOR_GRAY2BGR);
        var binaryImg = binaryBuf.update(bgrTmp);

        // --- rebuild structuring elements if size changed ---
        rebuildKernelsIfNeeded();

        // --- morphological openings (horiz/vert into persistent Mats for line extractor) ---
        Imgproc.morphologyEx(binary, morphHorizMat, Imgproc.MORPH_OPEN, seHoriz);
        Imgproc.morphologyEx(binary, morphVertMat,  Imgproc.MORPH_OPEN, seVert);
        Imgproc.morphologyEx(binary, morphOut,       Imgproc.MORPH_OPEN, seDiagFwd);
        var morphFwd = matToImage(morphOut, morphFwdBuf);
        Imgproc.morphologyEx(binary, morphOut,       Imgproc.MORPH_OPEN, seDiagBwd);
        var morphBwd = matToImage(morphOut, morphBwdBuf);

        var morphH = matToImage(morphHorizMat, morphHBuf);
        var morphV = matToImage(morphVertMat,  morphVBuf);

        // --- horizontal projection on morph-horiz (rows with horizontal strokes) ---
        Core.reduce(morphHorizMat, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);
        if (hRowSums.length != h) hRowSums = new float[h];
        rowSumMat.get(0, 0, hRowSums);

        // --- vertical projection on morph-vert (cols with vertical strokes) ---
        Core.reduce(morphVertMat, colSumMat, 0, Core.REDUCE_SUM, CvType.CV_32F);
        if (vColSums.length != w) vColSums = new float[w];
        colSumMat.get(0, 0, vColSums);

        return new PreProcessingResult(w, h, binaryImg,
                hRowSums.clone(), vColSums.clone(),
                morphH, morphV, morphFwd, morphBwd);
    }

    // -------------------------------------------------------------------------
    // binarisation strategies
    // -------------------------------------------------------------------------

    private void binarizeTophat() {
        int r = tophatRadius;
        if (r != lastTophatRadius) {
            lastTophatRadius = r;
            tophatSE.release();
            // square SE for top-hat (catches characters of any orientation)
            tophatSE.create(2 * r + 1, 2 * r + 1, CvType.CV_8U);
            tophatSE.setTo(new Scalar(1));
        }
        // white-hat: bright text on dark bg; black-hat: dark text on bright bg
        int op = appSettings.isDarkTheme() ? Imgproc.MORPH_TOPHAT : Imgproc.MORPH_BLACKHAT;
        Imgproc.morphologyEx(gray, tophat, op, tophatSE);
        // fixed threshold — top-hat output is already normalised relative to local contrast
        Imgproc.threshold(tophat, binary, tophatThreshold, 255, Imgproc.THRESH_BINARY);
    }

    private void binarizeAdaptive() {
        int block = adaptiveBlock | 1;  // ensure odd
        Imgproc.adaptiveThreshold(gray, binary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                appSettings.isDarkTheme() ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY,
                block, adaptiveC);
    }

    private void binarizeOtsu() {
        int flags = Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU;
        if (appSettings.isDarkTheme()) flags = Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU;
        Imgproc.threshold(gray, binary, 0, 255, flags);
    }

    private javafx.scene.image.WritableImage matToImage(Mat gray1ch, FxImageUtils.ImageBuffer buf) {
        Imgproc.cvtColor(gray1ch, bgrTmp, Imgproc.COLOR_GRAY2BGR);
        return buf.update(bgrTmp);
    }

    private void rebuildKernelsIfNeeded() {
        int hl = seHalfLen;
        if (hl == lastSeHalfLen) return;
        lastSeHalfLen = hl;
        int len = hl * 2 + 1;

        seHoriz.release();
        seVert.release();
        seDiagFwd.release();
        seDiagBwd.release();

        // horizontal —: 1×len row of ones
        seHoriz = Mat.ones(1, len, CvType.CV_8U);

        // vertical |: len×1 column of ones
        seVert = Mat.ones(len, 1, CvType.CV_8U);

        // diagonal /: anti-diagonal len×len, ones on anti-diagonal
        seDiagFwd = buildDiagKernel(len, false);

        // diagonal \: main-diagonal len×len, ones on main diagonal
        seDiagBwd = buildDiagKernel(len, true);
    }

    private static Mat buildDiagKernel(int size, boolean mainDiag) {
        Mat k = Mat.zeros(size, size, CvType.CV_8U);
        for (int i = 0; i < size; i++) {
            int j = mainDiag ? i : (size - 1 - i);
            k.put(i, j, 1);
        }
        return k;
    }

    public void release() {
        gray.release(); tophat.release(); tophatSE.release(); binary.release();
        rowSumMat.release(); colSumMat.release(); morphOut.release();
        morphHorizMat.release(); morphVertMat.release();
        seHoriz.release(); seVert.release(); seDiagFwd.release(); seDiagBwd.release();
        binaryBuf.release(); morphHBuf.release(); morphVBuf.release();
        morphFwdBuf.release(); morphBwdBuf.release(); bgrTmp.release();
    }
}
