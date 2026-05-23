package fr.an.textreco.model;

/**
 * Result of the grid detector for one frame.
 *
 * Detection pipeline (same for Y/X):
 *   1. raw valleys  — all sub-threshold local minima in the projection histogram
 *   2. diff histogram — histogram of inter-valley gaps; modal gap = best period
 *   3. filtered valleys — raw valleys that fit  offset + N × period  (within tolerance)
 *   4. Hough offset — minimum bin of acc[period] gives the gap phase
 */
public record GridDetectionResult(
        int frameWidth,
        int frameHeight,

        // --- Y axis (line grid) ---
        int    minLineH,
        int    maxLineH,
        double bestLineH,       // modal inter-valley gap (period), sub-pixel precision
        double bestLineY0,      // gap phase:  gap rows ≡ bestLineY0  (mod bestLineH)
        int[]  hValleys,        // all detected H-histogram valley midpoints
        int[]  hValleysFiltered,// valleys that match the periodic grid
        int[]  diffHistY,       // histogram of inter-valley gaps, index = gap size - minLineH
        float[][] accY,         // Hough accumulator [periodIdx][offset]

        // --- X axis (char grid) ---
        int    minCharW,
        int    maxCharW,
        double bestCharW,
        double bestCharX0,
        int[] vValleys,
        int[] vValleysFiltered,
        int[] diffHistX,
        float[][] accX
) {}
