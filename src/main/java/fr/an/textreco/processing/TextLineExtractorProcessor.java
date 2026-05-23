package fr.an.textreco.processing;

import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects horizontal text lines in a perspective-corrected BGR frame.
 *
 * Algorithm:
 *  1. Horizontal projection from morphHorizMat (opening with — kernel) → float[h] raw row sums
 *  2. Vertical   projection from morphVertMat  (opening with | kernel) → float[w] col sums (unused here)
 *  3. Box-smooth the projection with smoothRadius
 *  4. Find local minima (valleys) in the smoothed signal:
 *       a valley row v satisfies smoothed[v] < valleyThreshold * globalMax
 *       and is a local minimum within a ±valleyHalfWindow neighbourhood
 *  5. Spans between consecutive deep-enough valleys where the peak exceeds minPeakRatio
 *  6. Filter spans by minLineHeight / maxLineHeight; crop each into a WritableImage
 */
public class TextLineExtractorProcessor {

    // --- smoothing ---
    private final IntegerProperty smoothRadius    = new SimpleIntegerProperty(3);

    // --- valley detection ---
    private final DoubleProperty  valleyThreshold = new SimpleDoubleProperty(0.15);
    private final IntegerProperty valleyHalfWin   = new SimpleIntegerProperty(4);
    private final DoubleProperty  minPeakRatio    = new SimpleDoubleProperty(0.05);

    // --- span filtering ---
    private final IntegerProperty minLineHeight   = new SimpleIntegerProperty(6);
    private final IntegerProperty maxLineHeight   = new SimpleIntegerProperty(120);

    public IntegerProperty smoothRadiusProperty()    { return smoothRadius; }
    public int    getSmoothRadius()                  { return smoothRadius.get(); }
    public void   setSmoothRadius(int v)             { smoothRadius.set(v); }

    public DoubleProperty  valleyThresholdProperty() { return valleyThreshold; }
    public double getValleyThreshold()               { return valleyThreshold.get(); }
    public void   setValleyThreshold(double v)       { valleyThreshold.set(v); }

    public IntegerProperty valleyHalfWinProperty()   { return valleyHalfWin; }
    public int    getValleyHalfWin()                 { return valleyHalfWin.get(); }
    public void   setValleyHalfWin(int v)            { valleyHalfWin.set(v); }

    public DoubleProperty  minPeakRatioProperty()    { return minPeakRatio; }
    public double getMinPeakRatio()                  { return minPeakRatio.get(); }
    public void   setMinPeakRatio(double v)          { minPeakRatio.set(v); }

    public IntegerProperty minLineHeightProperty()   { return minLineHeight; }
    public int    getMinLineHeight()                 { return minLineHeight.get(); }
    public void   setMinLineHeight(int v)            { minLineHeight.set(v); }

    public IntegerProperty maxLineHeightProperty()   { return maxLineHeight; }
    public int    getMaxLineHeight()                 { return maxLineHeight.get(); }
    public void   setMaxLineHeight(int v)            { maxLineHeight.set(v); }

    // pre-allocated scratch Mats
    private final Mat rowSumMat = new Mat();

    // row buffers — reallocated only when frame height changes
    private float[] rowSums     = new float[0];
    private float[] smoothed    = new float[0];

    // per-line crop ImageBuffers — grown lazily, never shrunk
    private final List<FxImageUtils.ImageBuffer> lineBuffers = new ArrayList<>();

    public TextLineExtractorProcessor() {}

    /**
     * @param morphHorizMat  output of morphological opening with — kernel (identifies horizontal strokes)
     * @param morphVertMat   output of morphological opening with | kernel (not used for row detection but passed for future use)
     * @param warpedBgr      original colour frame — used only for cropping line images
     */
    public TextLineExtractionResult process(Mat morphHorizMat, Mat morphVertMat, Mat warpedBgr) {
        int w = morphHorizMat.cols();
        int h = morphHorizMat.rows();
        if (w == 0 || h == 0) {
            return new TextLineExtractionResult(w, h, List.of(), new float[0], new float[0], new int[0]);
        }

        // --- horizontal projection from morph-horiz (— opening detects text rows) ---
        Core.reduce(morphHorizMat, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);
        if (rowSums.length != h) { rowSums = new float[h]; smoothed = new float[h]; }
        rowSumMat.get(0, 0, rowSums);

        // --- smooth ---
        boxSmooth(rowSums, smoothed, h, smoothRadius.get());

        // --- global max of smoothed signal ---
        float globalMax = 1f;
        for (int r = 0; r < h; r++) if (smoothed[r] > globalMax) globalMax = smoothed[r];

        // --- find valley rows ---
        double vThresh  = valleyThreshold.get() * globalMax;
        int    vHalfWin = valleyHalfWin.get();
        double peakMin  = minPeakRatio.get() * globalMax;

        // Find local-minimum rows (candidate valley centres)
        List<Integer> valleyRows = new ArrayList<>();
        valleyRows.add(0);
        for (int r = 1; r < h - 1; r++) {
            if (smoothed[r] >= vThresh) continue;
            boolean isMin = true;
            int lo = Math.max(0, r - vHalfWin);
            int hi = Math.min(h - 1, r + vHalfWin);
            for (int k = lo; k <= hi; k++) {
                if (smoothed[k] < smoothed[r]) { isMin = false; break; }
            }
            if (isMin) valleyRows.add(r);
        }
        valleyRows.add(h);

        // Merge consecutive candidate valleys that have no peak between them,
        // then replace each valley with the midpoint of its low region.
        List<Integer> mergedValleys = new ArrayList<>();
        mergedValleys.add(0);
        for (int i = 1; i < valleyRows.size() - 1; i++) {
            int prev = mergedValleys.get(mergedValleys.size() - 1);
            int cur  = valleyRows.get(i);
            boolean hasPeak = false;
            for (int r = prev; r <= cur; r++) {
                if (smoothed[r] > peakMin) { hasPeak = true; break; }
            }
            if (!hasPeak) continue; // absorb into the previous valley region

            // Expand the low region around cur to its full extent below vThresh,
            // then use the midpoint as the separator.
            int regionLo = cur, regionHi = cur;
            while (regionLo > 0          && smoothed[regionLo - 1] < vThresh) regionLo--;
            while (regionHi < h - 1      && smoothed[regionHi + 1] < vThresh) regionHi++;
            mergedValleys.add((regionLo + regionHi) / 2);
        }
        mergedValleys.add(h);

        // --- build spans: between consecutive valley pairs where peak is strong enough ---
        double peakThresh = peakMin;
        int minH = minLineHeight.get(), maxH = maxLineHeight.get();
        List<TextLine> lines = new ArrayList<>();
        int bufIdx = 0;

        for (int i = 0; i < mergedValleys.size() - 1; i++) {
            int top    = mergedValleys.get(i);
            int bottom = mergedValleys.get(i + 1);
            int spanH  = bottom - top;
            if (spanH < minH || spanH > maxH) continue;

            // require a real peak in the span
            float spanPeak = 0f;
            for (int r = top; r < bottom; r++) if (smoothed[r] > spanPeak) spanPeak = smoothed[r];
            if (spanPeak < peakThresh) continue;

            Mat crop = warpedBgr.submat(new Rect(0, top, w, spanH));
            if (bufIdx >= lineBuffers.size()) lineBuffers.add(new FxImageUtils.ImageBuffer());
            lines.add(new TextLine(top, bottom, lineBuffers.get(bufIdx++).update(crop)));
        }

        int[] valleyArr = mergedValleys.stream().mapToInt(Integer::intValue).toArray();
        return new TextLineExtractionResult(w, h, lines, rowSums.clone(), smoothed.clone(), valleyArr);
    }

    // simple box (moving-average) filter
    private static void boxSmooth(float[] src, float[] dst, int n, int radius) {
        if (radius <= 0) { System.arraycopy(src, 0, dst, 0, n); return; }
        // prefix sums for O(n) sliding window
        double[] prefix = new double[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + src[i];
        for (int i = 0; i < n; i++) {
            int lo = Math.max(0, i - radius);
            int hi = Math.min(n - 1, i + radius);
            dst[i] = (float) ((prefix[hi + 1] - prefix[lo]) / (hi - lo + 1));
        }
    }

    public void release() {
        rowSumMat.release();
        lineBuffers.forEach(FxImageUtils.ImageBuffer::release);
        lineBuffers.clear();
    }
}
