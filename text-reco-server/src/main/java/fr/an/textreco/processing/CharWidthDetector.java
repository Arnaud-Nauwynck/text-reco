package fr.an.textreco.processing;

/**
 * Detects the dominant character width (X-axis period) from a vertical
 * projection histogram, smoothed across frames.
 *
 * <p>Stateless math lives in {@link CorrelationMath}; this class owns only the
 * cross-frame smoothing state for the char-width estimate.
 */
public class CharWidthDetector {

    private double smoothedCharWidth = -1;

    /**
     * @param colProjection vertical projection (one value per image column)
     * @param minW          minimum char-width to consider (px)
     * @param maxW          maximum char-width to consider (px)
     * @param alpha         exponential smoothing factor (0 = frozen, 1 = instant)
     * @return smoothed sub-pixel char-width, or -1 if it cannot be estimated
     *         and no previous estimate exists
     */
    public double detect(double[] colProjection, int minW, int maxW, double alpha) {
        double charWidth = CorrelationMath.estimatePeriodAutocorr(colProjection, minW, maxW);
        if (charWidth < 0) {
            return smoothedCharWidth; // keep last good value (or -1 if none yet)
        }
        smoothedCharWidth = CorrelationMath.smooth(smoothedCharWidth, charWidth, alpha);
        return smoothedCharWidth;
    }

    /**
     * Last smoothed char-width, or -1 if none has been computed yet.
     */
    public double getSmoothedCharWidth() {
        return smoothedCharWidth;
    }

    /**
     * Reset smoothing state (e.g. on source switch).
     */
    public void reset() {
        smoothedCharWidth = -1;
    }
}
