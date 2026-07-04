package fr.an.textreco.model;

import java.util.List;

public record CorrelationGridDetectionResult(
        int frameWidth,
        int frameHeight,

        double smoothedLineHeight,
        double smoothedPhase,
        double[] projection,
        List<Integer> linePositions,

        double smoothedCharWidth,
        double smoothedColPhase,
        double[] colProjection,
        List<Integer> columnPositions
) {
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
