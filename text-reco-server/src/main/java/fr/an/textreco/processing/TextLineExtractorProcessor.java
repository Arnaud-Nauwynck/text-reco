package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces text-line spans from a perspective-corrected frame using the periodic
 * grid formula: lineTop = gapPhase + N * bestLineH.
 * Line images are NOT stored here — the REST layer crops from the binary Mat on demand.
 */
public class TextLineExtractorProcessor {

    public TextLineExtractionResult process(float[] hRowSums, org.opencv.core.Mat binary,
                                            GridDetectionResult grid) {
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

        double y0 = gapPhase(hRowSums, h, lineH, grid.bestLineY0());
        int startN = (y0 >= 0) ? 0 : (int) Math.ceil(-y0 / lineH);

        List<TextLine> lines = new ArrayList<>();
        List<Integer> valleys = new ArrayList<>();

        for (int n = startN; ; n++) {
            int top = (int) Math.round(y0 + n * lineH);
            int bottom = (int) Math.round(y0 + (n + 1) * lineH);
            if (top >= h) break;
            bottom = Math.min(bottom, h);
            int spanH = bottom - top;
            if (spanH <= 0) break;
            valleys.add(top);
            lines.add(new TextLine(top, bottom));
        }
        valleys.add(h);

        int[] valleyArr = valleys.stream().mapToInt(Integer::intValue).toArray();
        return new TextLineExtractionResult(w, h, lines, hRowSums, new float[0], valleyArr);
    }

    private static double gapPhase(float[] rowSums, int h, double lineH, double fallback) {
        if (rowSums == null || rowSums.length < h || lineH <= 0) {
            return ((fallback % lineH) + lineH) % lineH;
        }
        final int sub = 4;
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
                if (energy < bestEnergy) { bestEnergy = energy; bestPhase = phase; }
            }
        }
        if (!anyInk || bestPhase < 0) {
            return ((fallback % lineH) + lineH) % lineH;
        }
        return bestPhase;
    }

    public void release() {
        // no resources owned
    }
}
