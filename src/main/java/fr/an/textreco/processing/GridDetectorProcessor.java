package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects a regular character grid (line-height and char-width).
 *
 * Pipeline for each axis (Y = line height, X = char width):
 *
 *   1. Valley detection — find midpoints of sub-threshold regions in the
 *      projection histogram (open+close combined).  Each midpoint is one
 *      inter-line (or inter-char) gap.
 *
 *   2. Difference histogram — histogram of consecutive inter-valley gaps.
 *      The modal bin (most frequent gap size) is the best period estimate.
 *      This is immune to harmonics: a gap of 60px when the true period is 30px
 *      simply means one valley was missed; it still votes in the 60px bin,
 *      not in the 30px bin — while real 30px gaps dominate.
 *
 *   3. Filter valleys — keep only those that lie within ±tolerance of
 *      some  offset + N × period  position.  Outliers are dropped.
 *
 *   4. Hough offset — build acc[p mod period] += signal[p] over the whole
 *      histogram.  The minimum bin is the gap phase (fewest strokes = gap).
 *
 *   Fallback — if fewer than 2 valleys are found the Hough-contrast method
 *   is used across the full candidate range.
 *
 * X range is constrained to [0.4, 0.7] × bestLineH after Y is resolved.
 */
public class GridDetectorProcessor {

    private final IntegerProperty minLineH = new SimpleIntegerProperty(25);
    private final IntegerProperty maxLineH = new SimpleIntegerProperty(80);
    private final IntegerProperty minCharW = new SimpleIntegerProperty(8);
    private final IntegerProperty maxCharW = new SimpleIntegerProperty(60);

    public IntegerProperty minLineHProperty() { return minLineH; }
    public int  getMinLineH()                 { return minLineH.get(); }
    public void setMinLineH(int v)            { minLineH.set(v); }

    public IntegerProperty maxLineHProperty() { return maxLineH; }
    public int  getMaxLineH()                 { return maxLineH.get(); }
    public void setMaxLineH(int v)            { maxLineH.set(v); }

    public IntegerProperty minCharWProperty() { return minCharW; }
    public int  getMinCharW()                 { return minCharW.get(); }
    public void setMinCharW(int v)            { minCharW.set(v); }

    public IntegerProperty maxCharWProperty() { return maxCharW; }
    public int  getMaxCharW()                 { return maxCharW.get(); }
    public void setMaxCharW(int v)            { maxCharW.set(v); }

    private final BooleanProperty forceLineH            = new SimpleBooleanProperty(false);
    private final DoubleProperty  forcedLineH           = new SimpleDoubleProperty(28.0);

    public BooleanProperty forceLineHProperty()          { return forceLineH; }
    public boolean         isForceLineH()                { return forceLineH.get(); }
    public void            setForceLineH(boolean v)      { forceLineH.set(v); }

    public DoubleProperty  forcedLineHProperty()         { return forcedLineH; }
    public double          getForcedLineH()              { return forcedLineH.get(); }
    public void            setForcedLineH(double v)      { forcedLineH.set(v); }

    /** When true, skip X-axis valley detection and use forcedCharWPx directly. */
    private final BooleanProperty forceCharWidth      = new SimpleBooleanProperty(false);
    /** Ratio lineH/charW (e.g. 2.0 means charW = lineH/2). Convenience: updates forcedCharWPx when lineH changes. */
    private final DoubleProperty  forcedCharWRatio    = new SimpleDoubleProperty(2.0);
    /** Direct forced char width in pixels. Used when forceCharWidth is true. */
    private final DoubleProperty  forcedCharWPx       = new SimpleDoubleProperty(15.0);

    public BooleanProperty forceCharWidthProperty()    { return forceCharWidth; }
    public boolean         isForceCharWidth()           { return forceCharWidth.get(); }
    public void            setForceCharWidth(boolean v) { forceCharWidth.set(v); }

    public DoubleProperty  forcedCharWRatioProperty()  { return forcedCharWRatio; }
    public double          getForcedCharWRatio()        { return forcedCharWRatio.get(); }
    public void            setForcedCharWRatio(double v){ forcedCharWRatio.set(v); }

    public DoubleProperty  forcedCharWPxProperty()     { return forcedCharWPx; }
    public double          getForcedCharWPx()           { return forcedCharWPx.get(); }
    public void            setForcedCharWPx(double v)   { forcedCharWPx.set(v); }

    private final BooleanProperty forceLineY0            = new SimpleBooleanProperty(false);
    private final DoubleProperty  forcedLineY0           = new SimpleDoubleProperty(0.0);

    public BooleanProperty forceLineY0Property()         { return forceLineY0; }
    public boolean         isForceLineY0()               { return forceLineY0.get(); }
    public void            setForceLineY0(boolean v)     { forceLineY0.set(v); }

    public DoubleProperty  forcedLineY0Property()        { return forcedLineY0; }
    public double          getForcedLineY0()             { return forcedLineY0.get(); }
    public void            setForcedLineY0(double v)     { forcedLineY0.set(v); }

    private final BooleanProperty forceCharX0            = new SimpleBooleanProperty(false);
    private final DoubleProperty  forcedCharX0           = new SimpleDoubleProperty(0.0);

    public BooleanProperty forceCharX0Property()         { return forceCharX0; }
    public boolean         isForceCharX0()               { return forceCharX0.get(); }
    public void            setForceCharX0(boolean v)     { forceCharX0.set(v); }

    public DoubleProperty  forcedCharX0Property()        { return forcedCharX0; }
    public double          getForcedCharX0()             { return forcedCharX0.get(); }
    public void            setForcedCharX0(double v)     { forcedCharX0.set(v); }

    private float[] rowSums = new float[0];
    private float[] colSums = new float[0];
    private final Mat reduceScratch = new Mat();

    public GridDetectionResult process(Mat morphHorizMat, Mat closeHorizMat,
                                       Mat morphVertMat,  Mat closeVertMat) {
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
        int minH = minLineH.get(), maxH = maxLineH.get();
        minH = Math.max(2, Math.min(minH, h / 2));
        maxH = Math.max(minH + 1, Math.min(maxH, h));

        // ---- Y axis ----
        int[]   hValleys  = detectValleys(hRowSums, h, minH);
        int[]   diffHistY = buildDiffHist(hValleys, minH, maxH);
        int[]   hValleysFiltered = hValleys;

        int numLineH = maxH - minH + 1;
        float[][] accY = buildAccumulator(hRowSums, h, minH, maxH, numLineH);

        double bestLineH;
        double bestLineY0;
        if (forceLineH.get()) {
            bestLineH = forcedLineH.get();
        } else {
            bestLineH = spanPeriod(hValleys, diffHistY, minH, maxH);
            if (bestLineH <= 0) {
                bestLineH = hValleys.length >= 2 ? bestFitPeriod(hValleys, h, minH, maxH) : 0;
                if (bestLineH <= 0) {
                    bestLineH = houghBestPeriod(accY, minH, numLineH)[0];
                }
            }
        }
        if (forceLineY0.get()) {
            bestLineY0 = forcedLineY0.get();
        } else if (bestLineH > 0) {
            bestLineY0 = medianOffset(hValleys, bestLineH);
        } else {
            bestLineY0 = houghBestPeriod(accY, minH, numLineH)[1];
        }

        // ---- X axis: range constrained to [0.4, 0.7] × bestLineH ----
        int minW = Math.max(minCharW.get(), (int) Math.round(0.4 * bestLineH));
        int maxW = Math.min(maxCharW.get(), (int) Math.round(0.7 * bestLineH));
        minW = Math.max(2, Math.min(minW, w / 2));
        maxW = Math.max(minW + 1, Math.min(maxW, w));
        int numCharW = maxW - minW + 1;

        int[]   vValleys  = detectValleys(vColSums, w, minW);
        int[]   diffHistX = buildDiffHist(vValleys, minW, maxW);
        int[]   vValleysFiltered = vValleys;
        float[][] accX = buildAccumulator(vColSums, w, minW, maxW, numCharW);

        double bestCharW;
        double bestCharX0;
        if (forceCharWidth.get()) {
            bestCharW = Math.max(0.1, forcedCharWPx.get());
        } else {
            bestCharW = spanPeriod(vValleys, diffHistX, minW, maxW);
            if (bestCharW <= 0) {
                bestCharW = vValleys.length >= 2 ? bestFitPeriod(vValleys, w, minW, maxW) : 0;
                if (bestCharW <= 0) {
                    bestCharW = houghBestPeriod(accX, minW, numCharW)[0];
                }
            }
        }
        if (forceCharX0.get()) {
            bestCharX0 = forcedCharX0.get();
        } else {
            bestCharX0 = bestCharW > 0 ? medianOffset(vValleys, bestCharW)
                    : houghBestPeriod(accX, minW, numCharW)[1];
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
     */
    static int[] detectValleys(float[] sig, int n, int minT) {
        float globalMax = 0;
        for (int i = 0; i < n; i++) if (sig[i] > globalMax) globalMax = sig[i];
        if (globalMax == 0) return new int[0];

        float thresh  = 0.25f * globalMax;
        int   halfWin = Math.max(3, minT / 2);

        // raw local minima below threshold
        List<Integer> raw = new ArrayList<>();
        for (int r = halfWin; r < n - halfWin; r++) {
            if (sig[r] >= thresh) continue;
            boolean isMin = true;
            for (int k = r - halfWin; k <= r + halfWin; k++)
                if (sig[k] < sig[r]) { isMin = false; break; }
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
            while (lo > 0     && sig[lo - 1] <= thresh) lo--;
            while (hi < n - 1 && sig[hi + 1] <= thresh) hi++;
            merged.add((lo + hi) / 2);
            i = j + 1;
        }

        int[] result = merged.stream().mapToInt(Integer::intValue).toArray();

        // Trim boundary valleys that have no text peak on their outer side.
        // A valley is valid only if there is a value >= thresh between it and
        // the signal boundary (i.e. at least one text stroke outside the grid).
        int lo = 0, hi = result.length;
        if (hi > 0 && !hasPeakBefore(sig, result[lo], thresh))    lo++;
        if (hi > lo && !hasPeakAfter (sig, result[hi - 1], n, thresh)) hi--;

        if (lo == 0 && hi == result.length) return result;
        return Arrays.copyOfRange(result, lo, hi);
    }

    private static boolean hasPeakBefore(float[] sig, int valley, float thresh) {
        for (int i = 0; i < valley; i++) if (sig[i] >= thresh) return true;
        return false;
    }

    private static boolean hasPeakAfter(float[] sig, int valley, int n, float thresh) {
        for (int i = valley + 1; i < n; i++) if (sig[i] >= thresh) return true;
        return false;
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
     * Primary period estimator: uses the diff-histogram directly.
     *
     * 1. Find the modal bin (most frequent gap size).
     * 2. Collect all bins within ±25% of the modal value.
     * 3. Return their weighted mean (weighted by bin count), rounded to int.
     *
     * This is O(range) and does not suffer from the tolerance-bias of
     * bestFitPeriod: the weighted mean naturally pulls toward the true
     * fundamental frequency even when a few gaps are 1-2px off.
     *
     * Returns 0 if fewer than 2 total votes exist.
     */
    /**
     * Best period estimate from a set of valley positions.
     *
     * Primary: if ≥ 2 valleys, use (last - first) / (count - 1) — the span
     * divided by the number of gaps.  This is equivalent to a least-squares
     * fit of a uniform grid and is maximally precise because it spreads the
     * accumulated error over the full span rather than just one gap at a time.
     *
     * Falls back to the diff-histogram weighted mean when only 1 gap exists.
     * Returns 0 if fewer than 2 valleys or the estimate is outside [minT, maxT].
     */
    static double spanPeriod(int[] valleys, int[] diffHist, int minT, int maxT) {
        if (valleys.length >= 2) {
            double span = valleys[valleys.length - 1] - valleys[0];
            int    gaps = valleys.length - 1;
            double T    = span / gaps;
            if (T >= minT && T <= maxT) return T;
        }
        // fallback: weighted mean of diff-histogram modal cluster
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
        int    totalWeight = 0;
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
        int   bestT     = 0;
        for (int T = minT; T <= maxT; T++) {
            int tol = Math.max(1, T / 6);   // relative tolerance — fairer across T values
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
        int   bestT = minT, bestOff = 0;
        float bestScore = -1;
        for (int ti = 0; ti < numT; ti++) {
            int T = ti + minT;
            float maxV = 0, minV = Float.MAX_VALUE;
            int   minOff = 0;
            for (int o = 0; o < T && o < acc[ti].length; o++) {
                if (acc[ti][o] > maxV) maxV = acc[ti][o];
                if (acc[ti][o] < minV) { minV = acc[ti][o]; minOff = o; }
            }
            float score = maxV > 0 ? (maxV - minV) / maxV : 0;
            if (score > bestScore) { bestScore = score; bestT = T; bestOff = minOff; }
        }
        return new double[]{ bestT, bestOff };
    }

    // -------------------------------------------------------------------------
    // Mat projection helpers
    // -------------------------------------------------------------------------

    private void addReduceRow(Mat matA, Mat matB, int h, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        Mat m = new Mat();
        org.opencv.core.Core.reduce(reduceScratch, m, 1, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        m.get(0, 0, out);
        m.release();
    }

    private void addReduceCol(Mat matA, Mat matB, int w, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        Mat m = new Mat();
        org.opencv.core.Core.reduce(reduceScratch, m, 0, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        m.get(0, 0, out);
        m.release();
    }

    public void release() { reduceScratch.release(); }
}
