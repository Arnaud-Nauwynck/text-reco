package fr.an.textreco.model;

/**
 * Result of the Hough-period grid detector.
 *
 * Both axes are detected independently by accumulating votes:
 *   for each lit pixel at position p, vote for offset = p mod period,
 *   for every candidate period in [min..max].
 *
 * The accumulator is a 2D array [periodIndex][offset], where
 *   periodIndex = period - minPeriod, offset ∈ [0, period).
 * Because offsets live in [0,period) and period varies, the array is
 * stored flattened as float[numPeriods][maxPeriod] — unused cells are 0.
 */
public record GridDetectionResult(
        int frameWidth,
        int frameHeight,

        // --- Y axis (line grid) ---
        int   minLineH,       // smallest candidate line height
        int   maxLineH,       // largest  candidate line height
        int   bestLineH,      // detected line height (period)
        int   bestLineY0,     // detected line grid offset (y0 = first line top mod bestLineH)
        float[][] accY,       // [periodIdx][offset]  periodIdx = lineH - minLineH

        // --- X axis (char grid) ---
        int   minCharW,
        int   maxCharW,
        int   bestCharW,
        int   bestCharX0,
        float[][] accX        // [periodIdx][offset]  periodIdx = charW - minCharW
) {}
