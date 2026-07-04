package fr.an.textreco.processing;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.BinarizationMethod;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.model.PreProcessingSettings;
import fr.an.textreco.util.MatFacade;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes all pre-processing intermediates from a perspective-corrected BGR frame.
 * All Mats are pre-allocated; float[] buffers are reallocated only on dimension change.
 * Image data (binary, morph Mats) remains in this processor's fields and is encoded
 * on-demand by the REST layer via MatEncoderService.
 */
public class PreProcessingProcessor {

    private final PreProcessingSettings settings;
    private final AppSettings appSettings;

    // scratch Mats — binarisation pipeline
    private final Mat gray = MatFacade.alloc("PreProc.gray");
    private final Mat tophatSE = MatFacade.alloc("PreProc.tophatSE");
    private final Mat tophat = MatFacade.alloc("PreProc.tophat");
    public final Mat binary = MatFacade.alloc("PreProc.binary");

    private int lastTophatRadius = -1;

    // scratch Mats — projection + morph
    private final Mat rowSumMat = MatFacade.alloc("PreProc.rowSumMat");
    private final Mat colSumMat = MatFacade.alloc("PreProc.colSumMat");
    // persistent morph results — readable by REST controllers after process() returns
    public final Mat morphHorizMat = MatFacade.alloc("PreProc.morphHorizMat");
    public final Mat morphVertMat = MatFacade.alloc("PreProc.morphVertMat");
    public final Mat morphDiagFwdMat = MatFacade.alloc("PreProc.morphDiagFwdMat");
    public final Mat morphDiagBwdMat = MatFacade.alloc("PreProc.morphDiagBwdMat");
    public final Mat closeHorizMat = MatFacade.alloc("PreProc.closeHorizMat");
    public final Mat closeVertMat = MatFacade.alloc("PreProc.closeVertMat");
    public final Mat closeDiagFwdMat = MatFacade.alloc("PreProc.closeDiagFwdMat");
    public final Mat closeDiagBwdMat = MatFacade.alloc("PreProc.closeDiagBwdMat");
    private final Mat morphOut = MatFacade.alloc("PreProc.morphOut");

    private int lastSeHalfLen = -1;
    private Mat seHoriz = MatFacade.alloc("PreProc.seHoriz");
    private Mat seVert = MatFacade.alloc("PreProc.seVert");
    private Mat seDiagFwd = MatFacade.alloc("PreProc.seDiagFwd");
    private Mat seDiagBwd = MatFacade.alloc("PreProc.seDiagBwd");

    private float[] hRowSums = new float[0];
    private float[] vColSums = new float[0];

    private final Mat histScratch = MatFacade.alloc("PreProc.histScratch");

    public PreProcessingProcessor(PreProcessingSettings settings, AppSettings appSettings) {
        this.settings = settings;
        this.appSettings = appSettings;
        rebuildKernelsIfNeeded();
    }

    public PreProcessingResult process(Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0) return null;

        Imgproc.cvtColor(warpedBgr, gray, Imgproc.COLOR_BGR2GRAY);

        switch (settings.getBinarizationMethod()) {
            case TOPHAT -> binarizeTophat();
            case ADAPTIVE -> binarizeAdaptive();
            case OTSU -> binarizeOtsu();
        }

        rebuildKernelsIfNeeded();

        Imgproc.morphologyEx(binary, morphHorizMat, Imgproc.MORPH_OPEN, seHoriz);
        Imgproc.morphologyEx(binary, morphVertMat, Imgproc.MORPH_OPEN, seVert);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_OPEN, seDiagFwd);
        morphOut.copyTo(morphDiagFwdMat);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_OPEN, seDiagBwd);
        morphOut.copyTo(morphDiagBwdMat);

        Imgproc.morphologyEx(binary, closeHorizMat, Imgproc.MORPH_CLOSE, seHoriz);
        Imgproc.morphologyEx(binary, closeVertMat, Imgproc.MORPH_CLOSE, seVert);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_CLOSE, seDiagFwd);
        morphOut.copyTo(closeDiagFwdMat);
        Imgproc.morphologyEx(binary, morphOut, Imgproc.MORPH_CLOSE, seDiagBwd);
        morphOut.copyTo(closeDiagBwdMat);

        Core.add(morphHorizMat, closeHorizMat, histScratch);
        Core.reduce(histScratch, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);
        if (hRowSums.length != h) hRowSums = new float[h];
        rowSumMat.get(0, 0, hRowSums);

        Core.add(morphVertMat, closeVertMat, histScratch);
        Core.reduce(histScratch, colSumMat, 0, Core.REDUCE_SUM, CvType.CV_32F);
        if (vColSums.length != w) vColSums = new float[w];
        colSumMat.get(0, 0, vColSums);

        int[] vValleys = detectValleys(vColSums, w);

        return new PreProcessingResult(w, h, hRowSums.clone(), vColSums.clone(), vValleys);
    }

    private void binarizeTophat() {
        int r = settings.getTophatRadius();
        if (r != lastTophatRadius) {
            lastTophatRadius = r;
            tophatSE.release();
            tophatSE.create(2 * r + 1, 2 * r + 1, CvType.CV_8U);
            tophatSE.setTo(new Scalar(1));
        }
        int op = appSettings.isDarkTheme() ? Imgproc.MORPH_TOPHAT : Imgproc.MORPH_BLACKHAT;
        Imgproc.morphologyEx(gray, tophat, op, tophatSE);
        Imgproc.threshold(tophat, binary, settings.getTophatThreshold(), 255, Imgproc.THRESH_BINARY);
    }

    private void binarizeAdaptive() {
        int block = settings.getAdaptiveBlock() | 1;
        Imgproc.adaptiveThreshold(gray, binary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                appSettings.isDarkTheme() ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY,
                block, settings.getAdaptiveC());
    }

    private void binarizeOtsu() {
        int flags = Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU;
        if (appSettings.isDarkTheme()) flags = Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU;
        Imgproc.threshold(gray, binary, 0, 255, flags);
    }

    private void rebuildKernelsIfNeeded() {
        int hl = settings.getSeHalfLen();
        if (hl == lastSeHalfLen) return;
        lastSeHalfLen = hl;
        int len = hl * 2 + 1;

        MatFacade.release(seHoriz, "PreProc.seHoriz");
        MatFacade.release(seVert, "PreProc.seVert");
        MatFacade.release(seDiagFwd, "PreProc.seDiagFwd");
        MatFacade.release(seDiagBwd, "PreProc.seDiagBwd");

        seHoriz = MatFacade.allocOnes(1, len, CvType.CV_8U, "PreProc.seHoriz");
        seVert = MatFacade.allocOnes(len, 1, CvType.CV_8U, "PreProc.seVert");
        seDiagFwd = buildDiagKernel(len, false);
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

    private static int[] detectValleys(float[] sums, int n) {
        float globalMax = 1f;
        for (int i = 0; i < n; i++) if (sums[i] > globalMax) globalMax = sums[i];

        double vThresh = 0.25 * globalMax;
        double peakMin = 0.05 * globalMax;
        int halfWin = 3;

        List<Integer> candidates = new ArrayList<>();
        for (int i = 1; i < n - 1; i++) {
            if (sums[i] >= vThresh) continue;
            boolean isMin = true;
            for (int k = Math.max(0, i - halfWin); k <= Math.min(n - 1, i + halfWin); k++)
                if (sums[k] < sums[i]) { isMin = false; break; }
            if (isMin) candidates.add(i);
        }

        List<Integer> result = new ArrayList<>();
        int ci = 0;
        while (ci < candidates.size()) {
            int groupStart = ci;
            while (ci + 1 < candidates.size()) {
                boolean hasPeak = false;
                for (int x = candidates.get(ci); x <= candidates.get(ci + 1); x++)
                    if (sums[x] > peakMin) { hasPeak = true; break; }
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
        MatFacade.release(morphDiagFwdMat, "PreProc.morphDiagFwdMat");
        MatFacade.release(morphDiagBwdMat, "PreProc.morphDiagBwdMat");
        MatFacade.release(closeHorizMat, "PreProc.closeHorizMat");
        MatFacade.release(closeVertMat, "PreProc.closeVertMat");
        MatFacade.release(closeDiagFwdMat, "PreProc.closeDiagFwdMat");
        MatFacade.release(closeDiagBwdMat, "PreProc.closeDiagBwdMat");
        MatFacade.release(seHoriz, "PreProc.seHoriz");
        MatFacade.release(seVert, "PreProc.seVert");
        MatFacade.release(seDiagFwd, "PreProc.seDiagFwd");
        MatFacade.release(seDiagBwd, "PreProc.seDiagBwd");
    }
}
