package fr.an.textreco.processing;

import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects horizontal text lines in a perspective-corrected BGR frame.
 *
 * Algorithm:
 *  1. Receives pre-computed hRowSums (open + close combined) from PreProcessingProcessor
 *  2. Box-smooth the row sums with smoothRadius
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

    // row buffers — reallocated only when frame height changes
    private float[] smoothed    = new float[0];

    // per-line crop ImageBuffers — grown lazily, never shrunk
    private final List<FxImageUtils.ImageBuffer> lineBuffers = new ArrayList<>();

    public TextLineExtractorProcessor() {}

    /**
     * @param hRowSums   horizontal projection (open+close combined), length == warpedBgr.rows()
     * @param warpedBgr  original colour frame — used only for cropping line images
     */
    public TextLineExtractionResult process(float[] hRowSums, Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0 || hRowSums.length != h) {
            return new TextLineExtractionResult(w, h, List.of(), new float[0], new float[0], new int[0]);
        }

        // --- smooth the combined open+close row-sum signal ---
        if (smoothed.length != h) smoothed = new float[h];
        boxSmooth(hRowSums, smoothed, h, smoothRadius.get());

        // --- global max of smoothed signal ---
        float globalMax = 1f;
        for (int r = 0; r < h; r++) if (smoothed[r] > globalMax) globalMax = smoothed[r];

        double vThresh  = valleyThreshold.get() * globalMax;
        int    vHalfWin = valleyHalfWin.get();
        double peakMin  = minPeakRatio.get() * globalMax;

        // --- find candidate valley rows: local minima below vThresh ---
        List<Integer> candidates = new ArrayList<>();
        for (int r = 1; r < h - 1; r++) {
            if (smoothed[r] >= vThresh) continue;
            boolean isMin = true;
            int lo = Math.max(0, r - vHalfWin);
            int hi = Math.min(h - 1, r + vHalfWin);
            for (int k = lo; k <= hi; k++) {
                if (smoothed[k] < smoothed[r]) { isMin = false; break; }
            }
            if (isMin) candidates.add(r);
        }

        // --- merge consecutive candidates with no intervening peak, pick best midpoint ---
        // Each valley separator is the midpoint of the contiguous sub-threshold region
        // that contains the deepest minimum between two text peaks.
        List<Integer> mergedValleys = new ArrayList<>();
        mergedValleys.add(0);

        int ci = 0;
        while (ci < candidates.size()) {
            // Collect all candidates in the same sub-threshold region (no peak between them)
            int groupStart = ci;
            while (ci + 1 < candidates.size()) {
                boolean hasPeak = false;
                for (int r = candidates.get(ci); r <= candidates.get(ci + 1); r++) {
                    if (smoothed[r] > peakMin) { hasPeak = true; break; }
                }
                if (hasPeak) break;
                ci++;
            }
            // candidates[groupStart..ci] are one contiguous valley group
            // Find the deepest point in the group
            int deepest = candidates.get(groupStart);
            for (int g = groupStart; g <= ci; g++) {
                int r = candidates.get(g);
                if (smoothed[r] < smoothed[deepest]) deepest = r;
            }
            // Expand from deepest outward while still below vThresh to get the full flat bottom
            int regionLo = deepest, regionHi = deepest;
            while (regionLo > 0     && smoothed[regionLo - 1] <= vThresh) regionLo--;
            while (regionHi < h - 1 && smoothed[regionHi + 1] <= vThresh) regionHi++;
            mergedValleys.add((regionLo + regionHi) / 2);
            ci++;
        }
        mergedValleys.add(h);

        // --- build spans between valley separators, filter by height and peak strength ---
        int minH = minLineHeight.get(), maxH = maxLineHeight.get();
        List<TextLine> lines = new ArrayList<>();
        int bufIdx = 0;

        for (int i = 0; i < mergedValleys.size() - 1; i++) {
            int top    = mergedValleys.get(i);
            int bottom = mergedValleys.get(i + 1);
            int spanH  = bottom - top;
            if (spanH < minH || spanH > maxH) continue;

            float spanPeak = 0f;
            for (int r = top; r < bottom; r++) if (smoothed[r] > spanPeak) spanPeak = smoothed[r];
            if (spanPeak < peakMin) continue;

            Mat crop = warpedBgr.submat(new Rect(0, top, w, spanH));
            if (bufIdx >= lineBuffers.size()) lineBuffers.add(new FxImageUtils.ImageBuffer());
            lines.add(new TextLine(top, bottom, lineBuffers.get(bufIdx++).update(crop)));
        }

        int[] valleyArr = mergedValleys.stream().mapToInt(Integer::intValue).toArray();
        return new TextLineExtractionResult(w, h, lines, hRowSums.clone(), smoothed.clone(), valleyArr);
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
        lineBuffers.forEach(FxImageUtils.ImageBuffer::release);
        lineBuffers.clear();
    }
}
