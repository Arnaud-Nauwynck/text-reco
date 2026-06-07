package fr.an.textreco.model;

import java.util.List;

/**
 * Result of the correlation-based grid detector for one frame.
 *
 * <p>Diagnostic fields (projections, smoothed periods, smoothed phases) are
 * exposed so each correlation view can render its projection histogram and the
 * fitted grid overlay without re-running the algorithm.  The Y-axis fields
 * (line height / line offset) and X-axis fields (char width / column offset)
 * each back one of the four correlation tabs.
 */
public record CorrelationGridDetectionResult(
        int frameWidth,
        int frameHeight,

        // --- Y axis (line grid) ---
        double smoothedLineHeight,  // sub-pixel line-height estimate after EMA
        double smoothedPhase,       // line gap phase in [0, smoothedLineHeight)
        double[] projection,        // horizontal (row) projection used for this frame
        List<Integer> linePositions,// Y positions of text-line tops

        // --- X axis (char grid) ---
        double smoothedCharWidth,   // sub-pixel char-width estimate after EMA
        double smoothedColPhase,    // column gap phase in [0, smoothedCharWidth)
        double[] colProjection,     // vertical (column) projection used for this frame
        List<Integer> columnPositions // X positions of character-cell centres
) {
    /**
     * Convert to a {@link GridDetectionResult} so the rest of the pipeline (OCR, overlay) can use it uniformly.
     */
    public GridDetectionResult toGridDetectionResult() {
        int[] hValleys = linePositions.stream().mapToInt(Integer::intValue).toArray();
        int[] vValleys = columnPositions.stream().mapToInt(Integer::intValue).toArray();
        return new GridDetectionResult(
                frameWidth, frameHeight,
                (int) Math.round(smoothedLineHeight), (int) Math.round(smoothedLineHeight),
                smoothedLineHeight, smoothedPhase,
                hValleys, hValleys,
                new int[0], new float[0][],
                (int) Math.round(smoothedCharWidth), (int) Math.round(smoothedCharWidth),
                smoothedCharWidth, smoothedColPhase,
                vValleys, vValleys,
                new int[0], new float[0][]);
    }
}
