package fr.an.textreco.processing;

import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import lombok.Getter;
import lombok.Setter;
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
    @Getter @Setter private volatile int    smoothRadius    = 3;    // box-filter half-width in rows

    // --- valley detection ---
    @Getter @Setter private volatile double valleyThreshold = 0.15; // valley must be below this fraction of global max
    @Getter @Setter private volatile int    valleyHalfWin   = 4;    // local-minimum search half-window (rows)
    @Getter @Setter private volatile double minPeakRatio    = 0.05; // span peak must exceed this fraction of global max

    // --- span filtering ---
    @Getter @Setter private volatile int    minLineHeight   = 6;
    @Getter @Setter private volatile int    maxLineHeight   = 120;

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
        boxSmooth(rowSums, smoothed, h, smoothRadius);

        // --- global max of smoothed signal ---
        float globalMax = 1f;
        for (int r = 0; r < h; r++) if (smoothed[r] > globalMax) globalMax = smoothed[r];

        // --- find valley rows ---
        // A row is a valley if:
        //   smoothed[r] < valleyThreshold * globalMax  (deep enough)
        //   AND smoothed[r] is a local minimum within ±valleyHalfWin
        double vThresh  = valleyThreshold * globalMax;
        int    vHalfWin = valleyHalfWin;
        List<Integer> valleyRows = new ArrayList<>();
        // always treat row 0 and row h as implicit boundaries
        valleyRows.add(0);
        for (int r = 1; r < h - 1; r++) {
            if (smoothed[r] >= vThresh) continue;
            // local minimum check
            boolean isMin = true;
            int lo = Math.max(0, r - vHalfWin);
            int hi = Math.min(h - 1, r + vHalfWin);
            for (int k = lo; k <= hi; k++) {
                if (smoothed[k] < smoothed[r]) { isMin = false; break; }
            }
            if (isMin) valleyRows.add(r);
        }
        valleyRows.add(h);

        // merge consecutive valleys that are adjacent (no peak between them)
        List<Integer> mergedValleys = new ArrayList<>();
        mergedValleys.add(valleyRows.get(0));
        for (int i = 1; i < valleyRows.size() - 1; i++) {
            int prev = mergedValleys.get(mergedValleys.size() - 1);
            int cur  = valleyRows.get(i);
            // check if any peak exists between prev and cur
            boolean hasPeak = false;
            double peakMin = minPeakRatio * globalMax;
            for (int r = prev; r <= cur; r++) {
                if (smoothed[r] > peakMin) { hasPeak = true; break; }
            }
            if (hasPeak) mergedValleys.add(cur);
            // else: skip this valley — it's just noise between two adjacent valleys
        }
        mergedValleys.add(valleyRows.get(valleyRows.size() - 1));

        // --- build spans: between consecutive valley pairs where peak is strong enough ---
        double peakThresh = minPeakRatio * globalMax;
        int minH = minLineHeight, maxH = maxLineHeight;
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

            // tighten the span to actual active rows within the valley boundaries
            int tight0 = top, tight1 = bottom;
            while (tight0 < bottom && smoothed[tight0] < peakThresh) tight0++;
            while (tight1 > tight0  && smoothed[tight1 - 1] < peakThresh) tight1--;
            int tH = tight1 - tight0;
            if (tH < minH || tH > maxH) continue;

            Mat crop = warpedBgr.submat(new Rect(0, tight0, w, tH));
            if (bufIdx >= lineBuffers.size()) lineBuffers.add(new FxImageUtils.ImageBuffer());
            lines.add(new TextLine(tight0, tight1, lineBuffers.get(bufIdx++).update(crop)));
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
