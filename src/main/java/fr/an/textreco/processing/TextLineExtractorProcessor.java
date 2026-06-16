package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces text-line crops from a perspective-corrected frame using the periodic
 * grid formula:  lineTop = gapPhase + N * bestLineH
 * <p>
 * Each band spans one period and is bounded by the inter-line gaps, so the text
 * row sits centred inside it.  The gap phase is recomputed here from the actual
 * row-sum projection rather than taken from {@code grid.bestLineY0()}: the grid
 * offset's meaning differs between detectors (valley-midpoint vs. correlation
 * cell-centre) and can land half a period off, which would split each crop
 * across two text rows.  Deriving the gap directly keeps the crops aligned with
 * whatever the projection shows.
 * <p>
 * Lines are generated for all N where the strip [lineTop, lineTop+lineH) lies
 * within the frame.  The hRowSums are carried through unchanged for display.
 */
public class TextLineExtractorProcessor {

    /**
     * Upper bound on the number of text lines a frame can produce, hence the
     * fixed size of the {@link #lineBuffers} pool. Even a small font on a tall
     * frame stays well under this; sizing the pool to a constant during init
     * keeps sub-pixel {@code lineH} jitter from growing it one buffer at a time
     * inside the run loop (which would leak a tracked Mat per growth).
     */
    private static final int MAX_LINES = 256;

    // per-line crop ImageBuffers — pre-allocated during init, reused every frame
    private final List<FxImageUtils.ImageBuffer> lineBuffers = new ArrayList<>(MAX_LINES);

    public TextLineExtractorProcessor() {
        // Pre-allocate the whole pool now (init mode), so no ImageBuffer — and
        // therefore no Mat — is ever allocated inside the per-frame run loop.
        for (int i = 0; i < MAX_LINES; i++) {
            lineBuffers.add(new FxImageUtils.ImageBuffer());
        }
    }

    /**
     * @param hRowSums horizontal projection (open+close combined), length == binary.rows()
     * @param binary   binarised single-channel frame — cropped for each line image
     * @param grid     detected grid parameters; if null an empty result is returned
     */
    public TextLineExtractionResult process(float[] hRowSums, Mat binary, GridDetectionResult grid) {
        int w = binary.cols();
        int h = binary.rows();
        if (w == 0 || h == 0 || grid == null) {
            return new TextLineExtractionResult(w, h, List.of(),
                    hRowSums == null ? new float[0] : hRowSums,
                    new float[0], new int[0]);
        }

        double lineH = grid.bestLineH();
        if (lineH <= 0) {
            return new TextLineExtractionResult(w, h, List.of(), hRowSums, new float[0], new int[0]);
        }

        // Gap phase derived from the projection (ink-minimising grid lines), so
        // each band is bounded by gaps and the text row sits centred inside it.
        // This self-corrects a grid offset that lands on text instead of a gap.
        double y0 = gapPhase(hRowSums, h, lineH, grid.bestLineY0());

        // Find the first N such that y0 + N*lineH >= 0
        int startN = (y0 >= 0) ? 0 : (int) Math.ceil(-y0 / lineH);

        List<TextLine> lines = new ArrayList<>();
        List<Integer> valleys = new ArrayList<>();
        int bufIdx = 0;

        for (int n = startN; ; n++) {
            int top = (int) Math.round(y0 + n * lineH);
            int bottom = (int) Math.round(y0 + (n + 1) * lineH);
            if (top >= h) break;
            bottom = Math.min(bottom, h);

            int spanH = bottom - top;
            if (spanH <= 0) break;

            valleys.add(top);

            // Pool is pre-sized to MAX_LINES; stop once exhausted rather than
            // allocating a new ImageBuffer (and a tracked Mat) inside the loop.
            if (bufIdx >= lineBuffers.size()) break;

            Mat crop = binary.submat(new Rect(0, top, w, spanH));
            lines.add(new TextLine(top, bottom, lineBuffers.get(bufIdx++).update(crop)));
            crop.release();
        }
        valleys.add(h);

        int[] valleyArr = valleys.stream().mapToInt(Integer::intValue).toArray();
        return new TextLineExtractionResult(w, h, lines, hRowSums, new float[0], valleyArr);
    }

    /**
     * Gap phase in [0, lineH) that minimises the summed projection at the grid
     * lines.  Text rows have high row-sums and gaps low ones, so the
     * ink-minimising phase marks the inter-line gaps — the correct band tops.
     *
     * @param fallback used only if the projection is unusable (all-zero); the
     *                 incoming grid offset, reduced into [0, lineH)
     */
    private static double gapPhase(float[] rowSums, int h, double lineH, double fallback) {
        if (rowSums == null || rowSums.length < h || lineH <= 0) {
            return ((fallback % lineH) + lineH) % lineH;
        }
        final int sub = 4;                                   // quarter-pixel scan
        int steps = Math.max(1, (int) Math.round(lineH * sub));
        double bestPhase = -1;
        double bestEnergy = Double.MAX_VALUE;
        boolean anyInk = false;
        for (int s = 0; s < steps; s++) {
            double phase = (double) s / sub;
            double sum = 0;
            int count = 0;
            for (double pos = phase; pos < h; pos += lineH) {
                int y = (int) Math.round(pos);
                if (y >= 0 && y < h) {
                    sum += rowSums[y];
                    count++;
                    if (rowSums[y] != 0f) anyInk = true;
                }
            }
            if (count > 0) {
                double energy = sum / count;
                if (energy < bestEnergy) {
                    bestEnergy = energy;
                    bestPhase = phase;
                }
            }
        }
        if (!anyInk || bestPhase < 0) {
            return ((fallback % lineH) + lineH) % lineH;
        }
        return bestPhase;
    }

    /**
     * Releases the native Mats backing the pool, then rebuilds it so a later
     * {@code start()} finds it pre-sized again. Called from the pipeline's
     * shutdown path while already back in {@link MatFacade.Mode#INIT}, so the
     * re-allocation is expected and unwarned.
     */
    public void release() {
        lineBuffers.forEach(FxImageUtils.ImageBuffer::release);
        lineBuffers.clear();
        for (int i = 0; i < MAX_LINES; i++) {
            lineBuffers.add(new FxImageUtils.ImageBuffer());
        }
    }
}
