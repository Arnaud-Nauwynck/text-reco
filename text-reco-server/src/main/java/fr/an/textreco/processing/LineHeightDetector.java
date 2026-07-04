package fr.an.textreco.processing;

/**
 * Detects the dominant text-line height (Y-axis period) from a horizontal
 * projection histogram, smoothed across frames.
 *
 * <p>Stateless math lives in {@link CorrelationMath}; this class owns only the
 * cross-frame smoothing state for the line-height estimate.
 */
public class LineHeightDetector {

    private double smoothedLineHeight = -1;

    /**
     * @param rowProjection horizontal projection (one value per image row)
     * @param minH          minimum line-height to consider (px)
     * @param maxH          maximum line-height to consider (px)
     * @param alpha         exponential smoothing factor (0 = frozen, 1 = instant)
     * @return smoothed sub-pixel line-height, or -1 if it cannot be estimated
     *         and no previous estimate exists
     */
    public double detect(double[] rowProjection, int minH, int maxH, double alpha) {
        double lineHeight = CorrelationMath.estimatePeriodAutocorr(rowProjection, minH, maxH);
        if (lineHeight < 0) {
            return smoothedLineHeight; // keep last good value (or -1 if none yet)
        }
        smoothedLineHeight = CorrelationMath.smooth(smoothedLineHeight, lineHeight, alpha);
        return smoothedLineHeight;
    }

    /**
     * Last smoothed line-height, or -1 if none has been computed yet.
     */
    public double getSmoothedLineHeight() {
        return smoothedLineHeight;
    }

    /**
     * Reset smoothing state (e.g. on source switch).
     */
    public void reset() {
        smoothedLineHeight = -1;
    }
}
