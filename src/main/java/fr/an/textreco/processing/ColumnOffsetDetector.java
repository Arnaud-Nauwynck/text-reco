package fr.an.textreco.processing;

/**
 * Detects the column-grid offset (X-axis gap phase) from a vertical projection
 * histogram, given the char-width, smoothed across frames.
 *
 * <p>The phase marks the inter-character gaps; character cells sit half a period
 * away.  Stateless math lives in {@link CorrelationMath}; this class owns only
 * the cross-frame (circular) smoothing state for the phase.
 */
public class ColumnOffsetDetector {

    private double smoothedPhase = -1;

    /**
     * @param colProjection vertical projection (one value per image column)
     * @param charWidth     resolved char-width (X period, px) — must be &gt; 0
     * @param alpha         exponential smoothing factor (0 = frozen, 1 = instant)
     * @return smoothed gap phase in [0, charWidth)
     */
    public double detect(double[] colProjection, double charWidth, double alpha) {
        double phase = CorrelationMath.findBestPhase(colProjection, charWidth);
        smoothedPhase = CorrelationMath.smoothPhase(smoothedPhase, phase, charWidth, alpha);
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
