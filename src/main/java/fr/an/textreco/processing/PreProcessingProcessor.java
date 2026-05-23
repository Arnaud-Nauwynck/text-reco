package fr.an.textreco.processing;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.util.FxImageUtils;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
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

    // binarisation — JavaFX properties, readable from background thread via .get()
    private final ObjectProperty<BinarizationMethod> binarizationMethod =
            new SimpleObjectProperty<>(BinarizationMethod.TOPHAT);
    private final IntegerProperty tophatRadius    = new SimpleIntegerProperty(12);
    private final IntegerProperty tophatThreshold = new SimpleIntegerProperty(20);
    private final IntegerProperty adaptiveBlock   = new SimpleIntegerProperty(31);
    private final IntegerProperty adaptiveC       = new SimpleIntegerProperty(10);
    private final IntegerProperty seHalfLen       = new SimpleIntegerProperty(7);

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

    public ObjectProperty<BinarizationMethod> binarizationMethodProperty() { return binarizationMethod; }
    public BinarizationMethod getBinarizationMethod()      { return binarizationMethod.get(); }
    public void setBinarizationMethod(BinarizationMethod m){ binarizationMethod.set(m); }

    public IntegerProperty tophatRadiusProperty()          { return tophatRadius; }
    public int  getTophatRadius()                          { return tophatRadius.get(); }
    public void setTophatRadius(int r)                     { tophatRadius.set(r); }

    public IntegerProperty tophatThresholdProperty()       { return tophatThreshold; }
    public int  getTophatThreshold()                       { return tophatThreshold.get(); }
    public void setTophatThreshold(int t)                  { tophatThreshold.set(t); }

    public IntegerProperty adaptiveBlockProperty()         { return adaptiveBlock; }
    public int  getAdaptiveBlock()                         { return adaptiveBlock.get(); }
    public void setAdaptiveBlock(int b)                    { adaptiveBlock.set(b); }

    public IntegerProperty adaptiveCProperty()             { return adaptiveC; }
    public int  getAdaptiveC()                             { return adaptiveC.get(); }
    public void setAdaptiveC(int c)                        { adaptiveC.set(c); }

    public IntegerProperty seHalfLenProperty()             { return seHalfLen; }
    public int  getSeHalfLen()                             { return seHalfLen.get(); }
    public void setSeHalfLen(int halfLen)                  { seHalfLen.set(halfLen); }

    public PreProcessingResult process(Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0) return null;

        Imgproc.cvtColor(warpedBgr, gray, Imgproc.COLOR_BGR2GRAY);

        switch (binarizationMethod.get()) {
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
        int r = tophatRadius.get();
        if (r != lastTophatRadius) {
            lastTophatRadius = r;
            tophatSE.release();
            tophatSE.create(2 * r + 1, 2 * r + 1, CvType.CV_8U);
            tophatSE.setTo(new Scalar(1));
        }
        int op = appSettings.isDarkTheme() ? Imgproc.MORPH_TOPHAT : Imgproc.MORPH_BLACKHAT;
        Imgproc.morphologyEx(gray, tophat, op, tophatSE);
        Imgproc.threshold(tophat, binary, tophatThreshold.get(), 255, Imgproc.THRESH_BINARY);
    }

    private void binarizeAdaptive() {
        int block = adaptiveBlock.get() | 1;
        Imgproc.adaptiveThreshold(gray, binary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                appSettings.isDarkTheme() ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY,
                block, adaptiveC.get());
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
        int hl = seHalfLen.get();
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
