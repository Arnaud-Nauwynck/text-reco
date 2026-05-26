package fr.an.textreco.model;

/**
 * Result of the grid detector for one frame.
 *
 * Detection pipeline (same for Y/X):
 *   1. First-pass valley detection with minT — feeds diff-histogram and
 *      Hough accumulator to estimate the best period.
 *   2. diff histogram — histogram of inter-valley gaps; modal gap = best period.
 *   3. Second-pass valley detection with minT = bestPeriod — halfWin = period/2
 *      collapses sub-period dips so each physical gap yields exactly one valley.
 *   4. Filter valleys — keep only those on  offset + N × period  (tolerance period/6).
 *   5. Hough / median offset — gap phase from filtered set.
 *
 * {@code hValleys}/{@code vValleys} are the second-pass (period-aware) valleys.
 * {@code hValleysFiltered}/{@code vValleysFiltered} are the on-grid subset.
 */
public record GridDetectionResult(
        int frameWidth,
        int frameHeight,

        // --- Y axis (line grid) ---
        int    minLineH,
        int    maxLineH,
        double bestLineH,         // best period estimate, sub-pixel precision
        double bestLineY0,        // gap phase:  gap rows ≡ bestLineY0  (mod bestLineH)
        int[]  hValleys,          // second-pass valleys (minT = bestLineH): one per gap
        int[]  hValleysFiltered,  // subset of hValleys fitting the periodic grid
        int[]  diffHistY,         // inter-valley gap histogram (first pass), index = gap - minLineH
        float[][] accY,           // Hough accumulator [periodIdx][offset]

        // --- X axis (char grid) ---
        int    minCharW,
        int    maxCharW,
        double bestCharW,
        double bestCharX0,
        int[]  vValleys,          // second-pass valleys (minT = bestCharW): one per gap
        int[]  vValleysFiltered,  // subset of vValleys fitting the periodic grid
        int[]  diffHistX,
        float[][] accX
) {}
