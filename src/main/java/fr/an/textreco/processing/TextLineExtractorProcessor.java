package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces text-line crops from a perspective-corrected frame using the periodic
 * grid formula:  lineTop = bestLineY0 + N * bestLineH
 *
 * Lines are generated for all N where the strip [lineTop, lineTop+lineH) lies
 * within the frame.  The hRowSums are carried through unchanged for display.
 */
public class TextLineExtractorProcessor {

    // per-line crop ImageBuffers — grown lazily, never shrunk
    private final List<FxImageUtils.ImageBuffer> lineBuffers = new ArrayList<>();

    public TextLineExtractorProcessor() {}

    /**
     * @param hRowSums   horizontal projection (open+close combined), length == warpedBgr.rows()
     * @param warpedBgr  original colour frame — used only for cropping line images
     * @param grid       detected grid parameters; if null an empty result is returned
     */
    public TextLineExtractionResult process(float[] hRowSums, Mat warpedBgr, GridDetectionResult grid) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0 || grid == null) {
            return new TextLineExtractionResult(w, h, List.of(),
                    hRowSums == null ? new float[0] : hRowSums,
                    new float[0], new int[0]);
        }

        double lineH = grid.bestLineH();
        double y0    = grid.bestLineY0();
        if (lineH <= 0) {
            return new TextLineExtractionResult(w, h, List.of(), hRowSums, new float[0], new int[0]);
        }

        // Find the first N such that y0 + N*lineH >= 0
        int startN = (y0 >= 0) ? 0 : (int) Math.ceil(-y0 / lineH);

        List<TextLine> lines   = new ArrayList<>();
        List<Integer>  valleys = new ArrayList<>();
        int bufIdx = 0;

        for (int n = startN; ; n++) {
            int top    = (int) Math.round(y0 + n * lineH);
            int bottom = (int) Math.round(y0 + (n + 1) * lineH);
            if (top >= h) break;
            bottom = Math.min(bottom, h);

            int spanH = bottom - top;
            if (spanH <= 0) break;

            valleys.add(top);

            Mat crop = warpedBgr.submat(new Rect(0, top, w, spanH));
            if (bufIdx >= lineBuffers.size()) lineBuffers.add(new FxImageUtils.ImageBuffer());
            lines.add(new TextLine(top, bottom, lineBuffers.get(bufIdx++).update(crop)));
        }
        valleys.add(h);

        int[] valleyArr = valleys.stream().mapToInt(Integer::intValue).toArray();
        return new TextLineExtractionResult(w, h, lines, hRowSums, new float[0], valleyArr);
    }

    public void release() {
        lineBuffers.forEach(FxImageUtils.ImageBuffer::release);
        lineBuffers.clear();
    }
}
