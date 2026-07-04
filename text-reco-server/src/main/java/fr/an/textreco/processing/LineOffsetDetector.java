package fr.an.textreco.processing;

/**
 * Detects the line-grid offset (Y-axis gap phase) from a horizontal projection
 * histogram, given the line-height, smoothed across frames.
 *
 * <p>The phase marks the inter-line gaps; text-line tops sit half a period
 * away.  Stateless math lives in {@link CorrelationMath}; this class owns only
 * the cross-frame (circular) smoothing state for the phase.
 */
public class LineOffsetDetector {

    private double smoothedPhase = -1;

    /**
     * @param rowProjection horizontal projection (one value per image row)
     * @param lineHeight    resolved line-height (Y period, px) — must be &gt; 0
     * @param alpha         exponential smoothing factor (0 = frozen, 1 = instant)
     * @return smoothed gap phase in [0, lineHeight)
     */
    public double detect(double[] rowProjection, double lineHeight, double alpha) {
        double phase = CorrelationMath.findBestPhase(rowProjection, lineHeight);
        smoothedPhase = CorrelationMath.smoothPhase(smoothedPhase, phase, lineHeight, alpha);
        return smoothedPhase;
    }

    /**
     * Last smoothed phase, or -1 if none has been computed yet.
     */
    public double getSmoothedPhase() {
        return smoothedPhase;
    }

    /**
     * Reset smoothing state (e.g. on source switch).
     */
    public void reset() {
        smoothedPhase = -1;
    }
}
