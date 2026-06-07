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

/**
 * Detects a regular character grid (line-height and char-width).
 * <p>
 * Pipeline for each axis (Y = line height, X = char width):
 * <p>
 * 1. Valley detection — find midpoints of sub-threshold regions in the
 * projection histogram (open+close combined).  Each midpoint is one
 * inter-line (or inter-char) gap.  All valleys are kept, including
 * boundary ones (first/last char-column or line-gap edges).
 * <p>
 * 2. Difference histogram — histogram of consecutive inter-valley gaps.
 * The modal bin (most frequent gap size) is the best period estimate.
 * This is immune to harmonics: a gap of 60px when the true period is 30px
 * simply means one valley was missed; it still votes in the 60px bin,
 * not in the 30px bin — while real 30px gaps dominate.
 * <p>
 * 3. Filter valleys — keep only those that lie within ±tolerance of
 * some  offset + N × period  position.  Outliers are dropped.
 * <p>
 * 4. Hough offset — build acc[p mod period] += signal[p] over the whole
 * histogram.  The minimum bin is the gap phase (fewest strokes = gap).
 * <p>
 * Fallback — if fewer than 2 valleys are found the Hough-contrast method
 * is used across the full candidate range.
 * <p>
 * X range is constrained to [0.2, 0.9] × bestLineH after Y is resolved,
 * supporting narrow terminal fonts where charW ≈ lineH/3.
 * <p>
 * All tunable parameters live in {@link GridDetectorSettings} (the Model).
 * This processor reads them via property.get() on every frame — no copies kept.
 */
public class GridDetectorProcessor {

    @Getter
    private final GridDetectorSettings settings;

    private float[] rowSums = new float[0];
    private float[] colSums = new float[0];
    private final Mat reduceScratch = MatFacade.alloc("GridDetector.reduceScratch");
    // reusable outputs for Core.reduce — pre-allocated to avoid per-call allocation
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
        int minH = settings.minLineH.get(), maxH = settings.maxLineH.get();
        minH = Math.max(2, Math.min(minH, h / 2));
        maxH = Math.max(minH + 1, Math.min(maxH, h));

        // ---- Y axis ----
        // First pass with minH to feed period-finding algorithms.
        int[] hValleysRaw = detectValleys(hRowSums, h, minH);
        int[] diffHistY = buildDiffHist(hValleysRaw, minH, maxH);

        int numLineH = maxH - minH + 1;
        float[][] accY = buildAccumulator(hRowSums, h, minH, maxH, numLineH);

        double bestLineH;
        double bestLineY0;
        if (settings.forceLineH.get()) {
            bestLineH = settings.forcedLineH.get();
        } else {
            bestLineH = spanPeriod(hValleysRaw, diffHistY, minH, maxH);
            if (bestLineH <= 0) {
                bestLineH = hValleysRaw.length >= 2 ? bestFitPeriod(hValleysRaw, h, minH, maxH) : 0;
                if (bestLineH <= 0) {
                    bestLineH = houghBestPeriod(accY, minH, numLineH)[0];
                }
            }
        }
        // Second pass: re-detect with minT = bestLineH so halfWin = lineH/2
        // collapses any sub-line dips into a single clean valley per gap.
        int[] hValleys = bestLineH > 0
                ? detectValleys(hRowSums, h, (int) Math.round(bestLineH))
                : hValleysRaw;

        if (settings.forceLineY0.get()) {
            bestLineY0 = settings.forcedLineY0.get();
        } else if (bestLineH > 0) {
            bestLineY0 = medianOffset(hValleys, bestLineH);
        } else {
            bestLineY0 = houghBestPeriod(accY, minH, numLineH)[1];
        }
        // Filter: keep only valleys on the resolved periodic grid.
        int[] hValleysFiltered = filterValleys(hValleys, bestLineH, bestLineY0);
        if (hValleysFiltered.length >= 2 && !settings.forceLineY0.get()) {
            bestLineY0 = medianOffset(hValleysFiltered, bestLineH);
        }

        // ---- X axis: range constrained to [0.2, 0.9] × bestLineH ----
        // Terminal fonts can be narrow (charW ≈ lineH/3), so lower bound is 0.2×lineH.
        int minW = Math.max(settings.minCharW.get(), (int) Math.round(0.2 * bestLineH));
        int maxW = Math.min(settings.maxCharW.get(), (int) Math.round(0.9 * bestLineH));
        minW = Math.max(2, Math.min(minW, w / 2));
        maxW = Math.max(minW + 1, Math.min(maxW, w));
        int numCharW = maxW - minW + 1;

        // First pass with minW to feed period-finding algorithms.
        int[] vValleysRaw = detectValleys(vColSums, w, minW);
        int[] diffHistX = buildDiffHist(vValleysRaw, minW, maxW);
        float[][] accX = buildAccumulator(vColSums, w, minW, maxW, numCharW);

        double bestCharW;
        double bestCharX0;
        if (settings.forceCharWidth.get()) {
            bestCharW = Math.max(0.1, settings.forcedCharWPx.get());
        } else {
            bestCharW = spanPeriod(vValleysRaw, diffHistX, minW, maxW);
            if (bestCharW <= 0) {
                bestCharW = vValleysRaw.length >= 2 ? bestFitPeriod(vValleysRaw, w, minW, maxW) : 0;
                if (bestCharW <= 0) {
                    bestCharW = houghBestPeriod(accX, minW, numCharW)[0];
                }
            }
        }
        // Second pass: re-detect with minT = bestCharW so halfWin = charW/2
        // suppresses intra-character strokes (which are narrower than charW).
        int[] vValleys = bestCharW > 0
                ? detectValleys(vColSums, w, (int) Math.round(bestCharW))
                : vValleysRaw;

        if (settings.forceCharX0.get()) {
            bestCharX0 = settings.forcedCharX0.get();
        } else {
            bestCharX0 = bestCharW > 0 ? medianOffset(vValleys, bestCharW)
                    : houghBestPeriod(accX, minW, numCharW)[1];
        }
        // Filter: keep only valleys on the resolved periodic grid.
        int[] vValleysFiltered = filterValleys(vValleys, bestCharW, bestCharX0);
        if (vValleysFiltered.length >= 2 && !settings.forceCharX0.get()) {
            bestCharX0 = medianOffset(vValleysFiltered, bestCharW);
        }

        return new GridDetectionResult(
                w, h,
                minH, maxH, bestLineH, bestLineY0,
                hValleys, hValleysFiltered, diffHistY, accY,
                minW, maxW, bestCharW, bestCharX0,
                vValleys, vValleysFiltered, diffHistX, accX);
    }

    // -------------------------------------------------------------------------
    // Step 1 — valley detection
    // -------------------------------------------------------------------------

    /**
     * Finds midpoints of sub-threshold flat regions in sig[0..n).
     * Threshold = 25% of global max.  Two candidate local minima within
     * minT/2 of each other are merged (keep deepest) so each physical gap
     * produces exactly one valley.
     * <p>
     * All detected valleys are returned, including those at the boundary of
     * the signal (first/last char columns or line gaps).  Boundary trimming
     * is intentionally omitted so that {@link #spanPeriod} can use the full
     * span — (last − first) / (count − 1) — for the most accurate period
     * estimate even when text fills the frame to the edges.
     */
    static int[] detectValleys(float[] sig, int n, int minT) {
        float globalMax = 0;
        for (int i = 0; i < n; i++) if (sig[i] > globalMax) globalMax = sig[i];
        if (globalMax == 0) return new int[0];

        float thresh = 0.25f * globalMax;
        int halfWin = Math.max(3, minT / 2);

        // raw local minima below threshold
        List<Integer> raw = new ArrayList<>();
        for (int r = halfWin; r < n - halfWin; r++) {
            if (sig[r] >= thresh) continue;
            boolean isMin = true;
            for (int k = r - halfWin; k <= r + halfWin; k++)
                if (sig[k] < sig[r]) {
                    isMin = false;
                    break;
                }
            if (isMin) raw.add(r);
        }

        // merge candidates within minT/2 of each other → keep deepest
        List<Integer> merged = new ArrayList<>();
        int i = 0;
        while (i < raw.size()) {
            int j = i;
            while (j + 1 < raw.size() && raw.get(j + 1) - raw.get(j) < minT / 2) j++;
            int deepest = raw.get(i);
            for (int k = i; k <= j; k++)
                if (sig[raw.get(k)] < sig[deepest]) deepest = raw.get(k);
            // expand to flat-bottom midpoint
            int lo = deepest, hi = deepest;
            while (lo > 0 && sig[lo - 1] <= thresh) lo--;
            while (hi < n - 1 && sig[hi + 1] <= thresh) hi++;
            merged.add((lo + hi) / 2);
            i = j + 1;
        }

        return merged.stream().mapToInt(Integer::intValue).toArray();
    }

    // -------------------------------------------------------------------------
    // Step 2 — difference histogram
    // -------------------------------------------------------------------------

    /**
     * Builds a histogram of consecutive inter-valley gaps.
     * Index 0 = gap of size minT, index k = gap of size minT+k.
     * Gaps outside [minT, maxT] are ignored.
     */
    static int[] buildDiffHist(int[] valleys, int minT, int maxT) {
        int[] hist = new int[maxT - minT + 1];
        for (int i = 0; i + 1 < valleys.length; i++) {
            int gap = valleys[i + 1] - valleys[i];
            if (gap >= minT && gap <= maxT)
                hist[gap - minT]++;
        }
        return hist;
    }

    // -------------------------------------------------------------------------
    // Step 3 — period from diff-histogram (primary path)
    // -------------------------------------------------------------------------

    /**
     * Best period estimate from a set of valley positions.
     * <p>
     * Primary: if ≥ 2 valleys, use (last - first) / (count - 1) — the span
     * divided by the number of gaps.  This is equivalent to a least-squares
     * fit of a uniform grid and is maximally precise because it spreads the
     * accumulated error over the full span rather than just one gap at a time.
     * <p>
     * Falls back to the diff-histogram weighted mean when only 1 gap exists.
     * Returns 0 if fewer than 2 valleys or the estimate is outside [minT, maxT].
     */
    static double spanPeriod(int[] valleys, int[] diffHist, int minT, int maxT) {
        if (valleys.length >= 2) {
            double span = valleys[valleys.length - 1] - valleys[0];
            int gaps = valleys.length - 1;
            double T = span / gaps;
            if (T >= minT && T <= maxT) return T;
        }
        // fallback: weighted mean of diff-histogram modal cluster
        return diffHistModalPeriod(diffHist, minT, maxT);
    }

    private static double diffHistModalPeriod(int[] hist, int minT, int maxT) {
        if (hist == null || hist.length == 0) return 0;
        int modalCount = 0, modalIdx = -1;
        for (int i = 0; i < hist.length; i++)
            if (hist[i] > modalCount) {
                modalCount = hist[i];
                modalIdx = i;
            }
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

    // -------------------------------------------------------------------------
    // Step 3b — filter valleys: keep only those on the periodic grid
    // -------------------------------------------------------------------------

    /**
     * Returns the subset of {@code valleys} that lie within {@code tol} pixels of
     * some grid position  {@code offset + N × period}  (N integer ≥ 0).
     * Tolerance is  max(1, period/6)  — same relative tolerance used in
     * {@link #bestFitPeriod}.
     */
    static int[] filterValleys(int[] valleys, double period, double offset) {
        if (valleys.length == 0 || period <= 0) return valleys;
        int tol = Math.max(1, (int) Math.round(period / 6.0));
        List<Integer> kept = new ArrayList<>();
        for (int v : valleys) {
            double shifted = ((v - offset) % period + period) % period;
            if (shifted <= tol || shifted >= period - tol) {
                kept.add(v);
            }
        }
        // If filtering removed everything (degenerate case), fall back to raw list.
        if (kept.isEmpty()) return valleys;
        return kept.stream().mapToInt(Integer::intValue).toArray();
    }

    // -------------------------------------------------------------------------
    // Step 4 — offset from valley phases (median, robust to jitter)
    // -------------------------------------------------------------------------

    /**
     * Returns the median of (valley % period) across all valleys.
     * The median is more robust than the mean when a few valleys are outliers.
     */
    static double medianOffset(int[] valleys, double period) {
        if (valleys.length == 0 || period <= 0) return 0;
        double[] phases = new double[valleys.length];
        for (int i = 0; i < valleys.length; i++) phases[i] = valleys[i] % period;
        Arrays.sort(phases);
        return phases[phases.length / 2];
    }

    // -------------------------------------------------------------------------
    // Fallback — grid-fit scoring (used when diff-histogram has < 2 votes)
    // -------------------------------------------------------------------------

    /**
     * Fallback: for each candidate T, find the offset maximising inliers
     * within ±T/6 (relative tolerance, not fixed).  Iterate T from minT
     * upward so the first T achieving the best score wins (prefers smaller T).
     */
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
            if (score > bestScore) {
                bestScore = score;
                bestT = T;
            }
        }
        return bestScore > 0 ? bestT : 0;
    }

    // -------------------------------------------------------------------------
    // Hough accumulator helpers
    // -------------------------------------------------------------------------

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
                if (acc[ti][o] < minV) {
                    minV = acc[ti][o];
                    minOff = o;
                }
            }
            float score = maxV > 0 ? (maxV - minV) / maxV : 0;
            if (score > bestScore) {
                bestScore = score;
                bestT = T;
                bestOff = minOff;
            }
        }
        return new double[]{bestT, bestOff};
    }

    // -------------------------------------------------------------------------
    // Mat projection helpers
    // -------------------------------------------------------------------------

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
