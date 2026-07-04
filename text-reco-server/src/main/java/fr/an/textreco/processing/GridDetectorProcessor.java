package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.GridDetectorSettings;
import fr.an.textreco.util.MatFacade;
import lombok.Getter;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GridDetectorProcessor {

    @Getter
    private final GridDetectorSettings settings;

    private float[] rowSums = new float[0];
    private float[] colSums = new float[0];
    private final Mat reduceScratch = MatFacade.alloc("GridDetector.reduceScratch");
    private final Mat reduceRowOut = MatFacade.alloc("GridDetector.reduceRowOut");
    private final Mat reduceColOut = MatFacade.alloc("GridDetector.reduceColOut");

    public GridDetectorProcessor(GridDetectorSettings settings) {
        this.settings = settings;
    }

    public GridDetectionResult process(Mat morphHorizMat, Mat closeHorizMat,
                                       Mat morphVertMat, Mat closeVertMat) {
        int w = morphHorizMat.cols();
        int h = morphHorizMat.rows();
        if (w == 0 || h == 0) return null;

        if (rowSums.length != h) rowSums = new float[h];
        addReduceRow(morphHorizMat, closeHorizMat, h, rowSums);

        if (colSums.length != w) colSums = new float[w];
        addReduceCol(morphVertMat, closeVertMat, w, colSums);

        return processFromSums(rowSums, h, colSums, w);
    }

    public GridDetectionResult processFromSums(float[] hRowSums, int h,
                                               float[] vColSums, int w) {
        int minH = settings.getMinLineH(), maxH = settings.getMaxLineH();
        minH = Math.max(2, Math.min(minH, h / 2));
        maxH = Math.max(minH + 1, Math.min(maxH, h));

        int[] hValleysRaw = detectValleys(hRowSums, h, minH);
        int[] diffHistY = buildDiffHist(hValleysRaw, minH, maxH);

        int numLineH = maxH - minH + 1;
        float[][] accY = buildAccumulator(hRowSums, h, minH, maxH, numLineH);

        double bestLineH;
        double bestLineY0;
        if (settings.isForceLineH()) {
            bestLineH = settings.getForcedLineH();
        } else {
            bestLineH = spanPeriod(hValleysRaw, diffHistY, minH, maxH);
            if (bestLineH <= 0) {
                bestLineH = hValleysRaw.length >= 2 ? bestFitPeriod(hValleysRaw, h, minH, maxH) : 0;
                if (bestLineH <= 0) {
                    bestLineH = houghBestPeriod(accY, minH, numLineH)[0];
                }
            }
        }
        int[] hValleys = bestLineH > 0
                ? detectValleys(hRowSums, h, (int) Math.round(bestLineH))
                : hValleysRaw;

        if (settings.isForceLineY0()) {
            bestLineY0 = settings.getForcedLineY0();
        } else if (bestLineH > 0) {
            bestLineY0 = medianOffset(hValleys, bestLineH);
        } else {
            bestLineY0 = houghBestPeriod(accY, minH, numLineH)[1];
        }
        int[] hValleysFiltered = filterValleys(hValleys, bestLineH, bestLineY0);
        if (hValleysFiltered.length >= 2 && !settings.isForceLineY0()) {
            bestLineY0 = medianOffset(hValleysFiltered, bestLineH);
        }

        int minW = Math.max(settings.getMinCharW(), (int) Math.round(0.2 * bestLineH));
        int maxW = Math.min(settings.getMaxCharW(), (int) Math.round(0.9 * bestLineH));
        minW = Math.max(2, Math.min(minW, w / 2));
        maxW = Math.max(minW + 1, Math.min(maxW, w));
        int numCharW = maxW - minW + 1;

        int[] vValleysRaw = detectValleys(vColSums, w, minW);
        int[] diffHistX = buildDiffHist(vValleysRaw, minW, maxW);
        float[][] accX = buildAccumulator(vColSums, w, minW, maxW, numCharW);

        double bestCharW;
        double bestCharX0;
        if (settings.isForceCharWidth()) {
            bestCharW = Math.max(0.1, settings.getForcedCharWPx());
        } else {
            bestCharW = spanPeriod(vValleysRaw, diffHistX, minW, maxW);
            if (bestCharW <= 0) {
                bestCharW = vValleysRaw.length >= 2 ? bestFitPeriod(vValleysRaw, w, minW, maxW) : 0;
                if (bestCharW <= 0) {
                    bestCharW = houghBestPeriod(accX, minW, numCharW)[0];
                }
            }
        }
        int[] vValleys = bestCharW > 0
                ? detectValleys(vColSums, w, (int) Math.round(bestCharW))
                : vValleysRaw;

        if (settings.isForceCharX0()) {
            bestCharX0 = settings.getForcedCharX0();
        } else {
            bestCharX0 = bestCharW > 0 ? medianOffset(vValleys, bestCharW)
                    : houghBestPeriod(accX, minW, numCharW)[1];
        }
        int[] vValleysFiltered = filterValleys(vValleys, bestCharW, bestCharX0);
        if (vValleysFiltered.length >= 2 && !settings.isForceCharX0()) {
            bestCharX0 = medianOffset(vValleysFiltered, bestCharW);
        }

        return new GridDetectionResult(
                w, h,
                minH, maxH, bestLineH, bestLineY0,
                hValleys, hValleysFiltered, diffHistY, accY,
                minW, maxW, bestCharW, bestCharX0,
                vValleys, vValleysFiltered, diffHistX, accX);
    }

    static int[] detectValleys(float[] sig, int n, int minT) {
        float globalMax = 0;
        for (int i = 0; i < n; i++) if (sig[i] > globalMax) globalMax = sig[i];
        if (globalMax == 0) return new int[0];

        float thresh = 0.25f * globalMax;
        int halfWin = Math.max(3, minT / 2);

        List<Integer> raw = new ArrayList<>();
        for (int r = halfWin; r < n - halfWin; r++) {
            if (sig[r] >= thresh) continue;
            boolean isMin = true;
            for (int k = r - halfWin; k <= r + halfWin; k++)
                if (sig[k] < sig[r]) { isMin = false; break; }
            if (isMin) raw.add(r);
        }

        List<Integer> merged = new ArrayList<>();
        int i = 0;
        while (i < raw.size()) {
            int j = i;
            while (j + 1 < raw.size() && raw.get(j + 1) - raw.get(j) < minT / 2) j++;
            int deepest = raw.get(i);
            for (int k = i; k <= j; k++)
                if (sig[raw.get(k)] < sig[deepest]) deepest = raw.get(k);
            int lo = deepest, hi = deepest;
            while (lo > 0 && sig[lo - 1] <= thresh) lo--;
            while (hi < n - 1 && sig[hi + 1] <= thresh) hi++;
            merged.add((lo + hi) / 2);
            i = j + 1;
        }

        return merged.stream().mapToInt(Integer::intValue).toArray();
    }

    static int[] buildDiffHist(int[] valleys, int minT, int maxT) {
        int[] hist = new int[maxT - minT + 1];
        for (int i = 0; i + 1 < valleys.length; i++) {
            int gap = valleys[i + 1] - valleys[i];
            if (gap >= minT && gap <= maxT)
                hist[gap - minT]++;
        }
        return hist;
    }

    static double spanPeriod(int[] valleys, int[] diffHist, int minT, int maxT) {
        if (valleys.length >= 2) {
            double span = valleys[valleys.length - 1] - valleys[0];
            int gaps = valleys.length - 1;
            double T = span / gaps;
            if (T >= minT && T <= maxT) return T;
        }
        return diffHistModalPeriod(diffHist, minT, maxT);
    }

    private static double diffHistModalPeriod(int[] hist, int minT, int maxT) {
        if (hist == null || hist.length == 0) return 0;
        int modalCount = 0, modalIdx = -1;
        for (int i = 0; i < hist.length; i++)
            if (hist[i] > modalCount) { modalCount = hist[i]; modalIdx = i; }
        if (modalCount < 2 || modalIdx < 0) return 0;
        int modalT = modalIdx + minT;
        int window = Math.max(1, modalT / 4);
        double weightedSum = 0;
        int totalWeight = 0;
        for (int i = 0; i < hist.length; i++) {
            int T = i + minT;
            if (Math.abs(T - modalT) <= window && hist[i] > 0) {
                weightedSum += (double) hist[i] * T;
                totalWeight += hist[i];
            }
        }
        return totalWeight == 0 ? modalT : weightedSum / totalWeight;
    }

    static int[] filterValleys(int[] valleys, double period, double offset) {
        if (valleys.length == 0 || period <= 0) return valleys;
        int tol = Math.max(1, (int) Math.round(period / 6.0));
        List<Integer> kept = new ArrayList<>();
        for (int v : valleys) {
            double shifted = ((v - offset) % period + period) % period;
            if (shifted <= tol || shifted >= period - tol) kept.add(v);
        }
        if (kept.isEmpty()) return valleys;
        return kept.stream().mapToInt(Integer::intValue).toArray();
    }

    static double medianOffset(int[] valleys, double period) {
        if (valleys.length == 0 || period <= 0) return 0;
        double[] phases = new double[valleys.length];
        for (int i = 0; i < valleys.length; i++) phases[i] = valleys[i] % period;
        Arrays.sort(phases);
        return phases[phases.length / 2];
    }

    static int bestFitPeriod(int[] valleys, int n, int minT, int maxT) {
        if (valleys.length < 2) return 0;
        float bestScore = -1;
        int bestT = 0;
        for (int T = minT; T <= maxT; T++) {
            int tol = Math.max(1, T / 6);
            int bestInliers = 0;
            for (int vi : valleys) {
                int o = vi % T;
                int inliers = 0;
                for (int vj : valleys) {
                    int phase = ((vj % T) - o + T) % T;
                    if (phase <= tol || phase >= T - tol) inliers++;
                }
                if (inliers > bestInliers) bestInliers = inliers;
            }
            float score = (float) bestInliers / valleys.length;
            if (score > bestScore) { bestScore = score; bestT = T; }
        }
        return bestScore > 0 ? bestT : 0;
    }

    private static float[][] buildAccumulator(float[] sig, int n, int minT, int maxT, int numT) {
        float[][] acc = new float[numT][maxT];
        for (int p = 0; p < n; p++) {
            float v = sig[p];
            if (v <= 0) continue;
            for (int T = minT; T <= maxT; T++)
                acc[T - minT][p % T] += v;
        }
        return acc;
    }

    private static double[] houghBestPeriod(float[][] acc, int minT, int numT) {
        int bestT = minT, bestOff = 0;
        float bestScore = -1;
        for (int ti = 0; ti < numT; ti++) {
            int T = ti + minT;
            float maxV = 0, minV = Float.MAX_VALUE;
            int minOff = 0;
            for (int o = 0; o < T && o < acc[ti].length; o++) {
                if (acc[ti][o] > maxV) maxV = acc[ti][o];
                if (acc[ti][o] < minV) { minV = acc[ti][o]; minOff = o; }
            }
            float score = maxV > 0 ? (maxV - minV) / maxV : 0;
            if (score > bestScore) { bestScore = score; bestT = T; bestOff = minOff; }
        }
        return new double[]{bestT, bestOff};
    }

    private void addReduceRow(Mat matA, Mat matB, int h, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        org.opencv.core.Core.reduce(reduceScratch, reduceRowOut, 1, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        reduceRowOut.get(0, 0, out);
    }

    private void addReduceCol(Mat matA, Mat matB, int w, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        org.opencv.core.Core.reduce(reduceScratch, reduceColOut, 0, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        reduceColOut.get(0, 0, out);
    }

    public void release() {
        MatFacade.release(reduceScratch, "GridDetector.reduceScratch");
        MatFacade.release(reduceRowOut, "GridDetector.reduceRowOut");
        MatFacade.release(reduceColOut, "GridDetector.reduceColOut");
    }
}
