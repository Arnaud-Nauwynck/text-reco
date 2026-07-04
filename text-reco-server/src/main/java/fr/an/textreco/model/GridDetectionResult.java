package fr.an.textreco.model;

public record GridDetectionResult(
        int frameWidth,
        int frameHeight,

        int minLineH,
        int maxLineH,
        double bestLineH,
        double bestLineY0,
        int[] hValleys,
        int[] hValleysFiltered,
        int[] diffHistY,
        float[][] accY,

        int minCharW,
        int maxCharW,
        double bestCharW,
        double bestCharX0,
        int[] vValleys,
        int[] vValleysFiltered,
        int[] diffHistX,
        float[][] accX
) {
}
