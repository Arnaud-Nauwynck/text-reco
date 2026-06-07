package fr.an.textreco.processing;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.PreProcessingSettings;
import fr.an.textreco.util.FxImageUtils;
import fr.an.textreco.util.MatFacade;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Computes all pre-processing intermediates from a perspective-corrected BGR frame:
 * - binarisation (top-hat, adaptive, or Otsu)
 * - horizontal and vertical projection histograms
 * - morphological opening with 4 line structuring elements: — | / \
 * <p>
 * All Mats are pre-allocated; float[] buffers are reallocated only on dimension change.
 */
public class PreProcessingProcessor {

    private final PreProcessingSettings settings;
    private final AppSettings appSettings;

    // scratch Mats — binarisation pipeline
    private final Mat gray = MatFacade.alloc("PreProc.gray");
    private final Mat tophatSE = MatFacade.alloc("PreProc.tophatSE");   // rebuilt when tophatRadius changes
    private final Mat tophat = MatFacade.alloc("PreProc.tophat");
    // persistent after process() returns — readable by the pipeline for line extraction
    public final Mat binary = MatFacade.alloc("PreProc.binary");

    private int lastTophatRadius = -1;

    // scratch Mats — projection + morph
    private final Mat rowSumMat = MatFacade.alloc("PreProc.rowSumMat");
    private final Mat colSumMat = MatFacade.alloc("PreProc.colSumMat");
    // persistent morph results — readable by TextLineExtractorProcessor after process() returns
    public final Mat morphHorizMat = MatFacade.alloc("PreProc.morphHorizMat");
    public final Mat morphVertMat = MatFacade.alloc("PreProc.morphVertMat");
    public final Mat closeHorizMat = MatFacade.alloc("PreProc.closeHorizMat");
    public final Mat closeVertMat = MatFacade.alloc("PreProc.closeVertMat");
    private final Mat morphOut = MatFacade.alloc("PreProc.morphOut");   // scratch for diag/closing ops

    // structuring elements — rebuilt when seHalfLen changes
    private int lastSeHalfLen = -1;
    private Mat seHoriz = MatFacade.alloc("PreProc.seHoriz");
    private Mat seVert = MatFacade.alloc("PreProc.seVert");
    private Mat seDiagFwd = MatFacade.alloc("PreProc.seDiagFwd");
    private Mat seDiagBwd = MatFacade.alloc("PreProc.seDiagBwd");

    // projection buffers
    private float[] hRowSums = new float[0];
    private float[] vColSums = new float[0];

    // ImageBuffers for FX conversion — one per output image
    private final FxImageUtils.ImageBuffer binaryBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphHBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphVBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphFwdBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer morphBwdBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer closeHBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer closeVBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer closeFwdBuf = new FxImageUtils.ImageBuffer();
    private final FxImageUtils.ImageBuffer closeBwdBuf = new FxImageUtils.ImageBuffer();

    // scratch Mat for histogram summing
    private final Mat histScratch = MatFacade.alloc("PreProc.histScratch");

    // scratch BGR wrapper for single-channel → BGR conversion before ImageBuffer
    private final Mat bgrTmp = MatFacade.alloc("PreProc.bgrTmp");

    public PreProcessingProcessor(PreProcessingSettings settings, AppSettings appSettings) {
        this.settings = settings;
        this.appSettings = appSettings;
        // Pre-build structuring elements during init so the first frame doesn't
        // allocate them inside the run loop. A later seHalfLen change will rebuild
        // them (and warn) — that is a genuine runtime config-change allocation.
        rebuildKernelsIfNeeded();
    }

    // Delegation accessors — kept for backward-compat; views should prefer settings.* directly
    public ObjectProperty<BinarizationMethod> binarizationMethodProperty() {
        return settings.binarizationMethod;
    }

    public IntegerProperty tophatRadiusProperty() {
        return settings.tophatRadius;
    }

    public IntegerProperty tophatThresholdProperty() {
        return settings.tophatThreshold;
    }

    public IntegerProperty adaptiveBlockProperty() {
        return settings.adaptiveBlock;
    }

    public IntegerProperty adaptiveCProperty() {
        return settings.adaptiveC;
    }

    public IntegerProperty seHalfLenProperty() {
        return settings.seHalfLen;
    }

    public PreProcessingResult process(Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0) return null;

        Imgproc.cvtColor(warpedBgr, gray, Imgproc.COLOR_BGR2GRAY);

        switch (settings.binarizationMethod.get()) {
            case TOPHAT -> binarizeTophat();
            case ADAPTIVE -> binarizeAdaptive();
            case OTSU -> binarizeOtsu();
        }

        // binary → BGR for ImageBuffer (expects 3-channel)
        Imgproc.cvtColor(binary, bgrTmp, Imgproc.COLOR_GRAY2BGR);
        var binaryImg = binaryBuf.update(bgrTmp);

        // --- rebuild structuring elements if size changed ---
        rebuildKernelsIfNeeded();

        // --- morphological openings (horiz/vert into persistent Mats for line extractor) ---
        Imgproc.morphologyEx(binary, morphHorizMat, Imgproc.MORPH_OPEN, seHoriz);
        Imgproc.morphologyEx(binary, morphVertMat, Imgproc.MORPH_OPEN, seVert);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_OPEN, seDiagFwd);
        var morphFwd = matToImage(morphOut, morphFwdBuf);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_OPEN, seDiagBwd);
        var morphBwd = matToImage(morphOut, morphBwdBuf);

        var morphH = matToImage(morphHorizMat, morphHBuf);
        var morphV = matToImage(morphVertMat, morphVBuf);

        // --- morphological closings (same 4 directions) ---
        Imgproc.morphologyEx(binary, closeHorizMat, Imgproc.MORPH_CLOSE, seHoriz);
        Imgproc.morphologyEx(binary, closeVertMat, Imgproc.MORPH_CLOSE, seVert);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_CLOSE, seDiagFwd);
        var closeFwd = matToImage(morphOut, closeFwdBuf);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_CLOSE, seDiagBwd);
        var closeBwd = matToImage(morphOut, closeBwdBuf);

        var closeH = matToImage(closeHorizMat, closeHBuf);
        var closeV = matToImage(closeVertMat, closeVBuf);

        // --- horizontal projection: sum of opening + closing on horiz SE ---
        Core.add(morphHorizMat, closeHorizMat, histScratch);
        Core.reduce(histScratch, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);
        if (hRowSums.length != h) hRowSums = new float[h];
        rowSumMat.get(0, 0, hRowSums);

        // --- vertical projection: sum of opening + closing on vert SE ---
        Core.add(morphVertMat, closeVertMat, histScratch);
        Core.reduce(histScratch, colSumMat, 0, Core.REDUCE_SUM, CvType.CV_32F);
        if (vColSums.length != w) vColSums = new float[w];
        colSumMat.get(0, 0, vColSums);

        int[] vValleys = detectValleys(vColSums, w);

        return new PreProcessingResult(w, h, binaryImg,
                hRowSums.clone(), vColSums.clone(), vValleys,
                morphH, morphV, morphFwd, morphBwd,
                closeH, closeV, closeFwd, closeBwd);
    }

    // -------------------------------------------------------------------------
    // binarisation strategies
    // -------------------------------------------------------------------------

    private void binarizeTophat() {
        int r = settings.tophatRadius.get();
        if (r != lastTophatRadius) {
            lastTophatRadius = r;
            tophatSE.release();
            tophatSE.create(2 * r + 1, 2 * r + 1, CvType.CV_8U);
            tophatSE.setTo(new Scalar(1));
        }
        int op = appSettings.isDarkTheme() ? Imgproc.MORPH_TOPHAT : Imgproc.MORPH_BLACKHAT;
        Imgproc.morphologyEx(gray, tophat, op, tophatSE);
        Imgproc.threshold(tophat, binary, settings.tophatThreshold.get(), 255, Imgproc.THRESH_BINARY);
    }

    private void binarizeAdaptive() {
        int block = settings.adaptiveBlock.get() | 1;
        Imgproc.adaptiveThreshold(gray, binary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                appSettings.isDarkTheme() ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY,
                block, settings.adaptiveC.get());
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
        int hl = settings.seHalfLen.get();
        if (hl == lastSeHalfLen) return;
        lastSeHalfLen = hl;
        int len = hl * 2 + 1;

        MatFacade.release(seHoriz, "PreProc.seHoriz");
        MatFacade.release(seVert, "PreProc.seVert");
        MatFacade.release(seDiagFwd, "PreProc.seDiagFwd");
        MatFacade.release(seDiagBwd, "PreProc.seDiagBwd");

        // horizontal —: 1×len row of ones
        seHoriz = MatFacade.allocOnes(1, len, CvType.CV_8U, "PreProc.seHoriz");

        // vertical |: len×1 column of ones
        seVert = MatFacade.allocOnes(len, 1, CvType.CV_8U, "PreProc.seVert");

        // diagonal /: anti-diagonal len×len, ones on anti-diagonal
        seDiagFwd = buildDiagKernel(len, false);

        // diagonal \: main-diagonal len×len, ones on main diagonal
        seDiagBwd = buildDiagKernel(len, true);
    }

    private static Mat buildDiagKernel(int size, boolean mainDiag) {
        Mat k = MatFacade.allocZeros(size, size, CvType.CV_8U, "PreProc.seDiag");
        for (int i = 0; i < size; i++) {
            int j = mainDiag ? i : (size - 1 - i);
            k.put(i, j, 1);
        }
        return k;
    }

    /**
     * Detects valley separators in a 1-D projection signal using the same
     * grouping + deepest-midpoint algorithm as TextLineExtractorProcessor.
     * Thresholds are fixed at 25% of global max (valley) and 5% (peak minimum).
     */
    private static int[] detectValleys(float[] sums, int n) {
        float globalMax = 1f;
        for (int i = 0; i < n; i++) if (sums[i] > globalMax) globalMax = sums[i];

        double vThresh = 0.25 * globalMax;
        double peakMin = 0.05 * globalMax;
        int halfWin = 3;

        // candidate local minima below vThresh
        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (int i = 1; i < n - 1; i++) {
            if (sums[i] >= vThresh) continue;
            boolean isMin = true;
            for (int k = Math.max(0, i - halfWin); k <= Math.min(n - 1, i + halfWin); k++)
                if (sums[k] < sums[i]) {
                    isMin = false;
                    break;
                }
            if (isMin) candidates.add(i);
        }

        // group consecutive candidates with no peak between them; keep deepest midpoint
        java.util.List<Integer> result = new java.util.ArrayList<>();
        int ci = 0;
        while (ci < candidates.size()) {
            int groupStart = ci;
            while (ci + 1 < candidates.size()) {
                boolean hasPeak = false;
                for (int x = candidates.get(ci); x <= candidates.get(ci + 1); x++)
                    if (sums[x] > peakMin) {
                        hasPeak = true;
                        break;
                    }
                if (hasPeak) break;
                ci++;
            }
            int deepest = candidates.get(groupStart);
            for (int g = groupStart; g <= ci; g++) {
                int x = candidates.get(g);
                if (sums[x] < sums[deepest]) deepest = x;
            }
            int lo = deepest, hi = deepest;
            while (lo > 0 && sums[lo - 1] <= vThresh) lo--;
            while (hi < n - 1 && sums[hi + 1] <= vThresh) hi++;
            result.add((lo + hi) / 2);
            ci++;
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    public void release() {
        MatFacade.release(gray, "PreProc.gray");
        MatFacade.release(tophat, "PreProc.tophat");
        MatFacade.release(tophatSE, "PreProc.tophatSE");
        MatFacade.release(binary, "PreProc.binary");
        MatFacade.release(rowSumMat, "PreProc.rowSumMat");
        MatFacade.release(colSumMat, "PreProc.colSumMat");
        MatFacade.release(morphOut, "PreProc.morphOut");
        MatFacade.release(histScratch, "PreProc.histScratch");
        MatFacade.release(morphHorizMat, "PreProc.morphHorizMat");
        MatFacade.release(morphVertMat, "PreProc.morphVertMat");
        MatFacade.release(closeHorizMat, "PreProc.closeHorizMat");
        MatFacade.release(closeVertMat, "PreProc.closeVertMat");
        MatFacade.release(seHoriz, "PreProc.seHoriz");
        MatFacade.release(seVert, "PreProc.seVert");
        MatFacade.release(seDiagFwd, "PreProc.seDiagFwd");
        MatFacade.release(seDiagBwd, "PreProc.seDiagBwd");
        binaryBuf.release();
        morphHBuf.release();
        morphVBuf.release();
        morphFwdBuf.release();
        morphBwdBuf.release();
        closeHBuf.release();
        closeVBuf.release();
        closeFwdBuf.release();
        closeBwdBuf.release();
        bgrTmp.release();
    }
}
