package fr.an.textreco.model;

import java.util.List;

/**
 * Result of the correlation-based grid detector for one frame.
 *
 * <p>Diagnostic fields (projection, smoothedLineHeight, smoothedPhase) are
 * exposed so the view can render the horizontal projection histogram and the
 * fitted grid overlay without re-running the algorithm.
 */
public record CorrelationGridDetectionResult(
        int frameWidth,
        int frameHeight,

        double smoothedLineHeight,  // sub-pixel line-height estimate after EMA
        double smoothedPhase,       // gap phase in [0, smoothedLineHeight)

        double[] projection,        // horizontal projection used for this frame

        List<Integer> linePositions // Y positions of text-line tops
) {
    /** Convert to a {@link GridDetectionResult} so the rest of the pipeline (OCR, overlay) can use it uniformly. */
    public GridDetectionResult toGridDetectionResult() {
        int[] hValleys = linePositions.stream().mapToInt(Integer::intValue).toArray();
        return new GridDetectionResult(
                frameWidth, frameHeight,
                (int) Math.round(smoothedLineHeight), (int) Math.round(smoothedLineHeight),
                smoothedLineHeight, smoothedPhase,
                hValleys, hValleys,
                new int[0], new float[0][],
                0, 0, 0.0, 0.0,
                new int[0], new int[0],
                new int[0], new float[0][]);
    }
}
