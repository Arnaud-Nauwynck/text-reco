package fr.an.textreco.processing;

/**
 * Pure 1-D signal math shared by the correlation grid detectors.
 *
 * <p>All methods are axis-agnostic: they operate on a projection histogram
 * (row-sums for the Y axis, column-sums for the X axis).  Period detection
 * ({@link #estimatePeriodAutocorr}) finds the dominant spacing; phase detection
 * ({@link #findBestPhase}) finds the gap offset within that spacing.
 *
 * <p>Stateless and side-effect free — the per-axis processors hold the
 * cross-frame smoothing state.
 */
final class CorrelationMath {

    private CorrelationMath() {
    }

    // ────────────────────────────────────────────────────────────────────────
    // Period — autocorrelation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Dominant period (line-height or char-width) of {@code proj} within
     * [{@code minT}, {@code maxT}], or -1 if it cannot be estimated.
     *
     * <p>Uses the unbiased autocorrelation coefficient, a 2×/3× harmonic
     * correction, and parabolic sub-pixel interpolation around the peak.
     */
    static double estimatePeriodAutocorr(double[] proj, int minT, int maxT) {
        int n = proj.length;
        if (n == 0) return -1;

        double mean = 0;
        for (double v : proj) mean += v;
        mean /= n;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) p[i] = proj[i] - mean;

        double bestVal = Double.NEGATIVE_INFINITY;
        int bestLag = -1;

        for (int lag = minT; lag <= maxT && lag < n; lag++) {
            double acc = autocorrAt(p, lag);
            if (acc > bestVal) {
                bestVal = acc;
                bestLag = lag;
            }
        }

        if (bestLag < 0) return -1;

        // Harmonic correction: the peak may land on 2×/3× the true period.
        // If a sub-multiple lag inside [minT, maxT] scores almost as high, prefer
        // it — the fundamental period is the real grid spacing.
        for (int div = 3; div >= 2; div--) {
            int sub = (int) Math.round((double) bestLag / div);
            if (sub >= minT && sub <= maxT && sub < n) {
                if (autocorrAt(p, sub) >= 0.85 * bestVal) {
                    bestLag = sub;
                    bestVal = autocorrAt(p, sub);
                }
            }
        }

        // Parabolic sub-pixel interpolation around the peak using the same
        // (unbiased) autocorrelation values that produced the integer peak.
        if (bestLag > minT && bestLag < maxT && bestLag < n - 1) {
            double vm = autocorrAt(p, bestLag - 1);
            double v0 = bestVal;
            double vp = autocorrAt(p, bestLag + 1);
            double denom = 2 * (2 * v0 - vm - vp);
            if (Math.abs(denom) > 1e-6) {
                double delta = (vm - vp) / denom;
                if (delta > -1 && delta < 1) return bestLag + delta;
            }
        }
        return bestLag;
    }

    /**
     * Unbiased (coefficient) autocorrelation at the given lag.
     *
     * <p>The raw sum {@code Σ p[y]·p[y+lag]} runs over {@code n−lag} terms, so it
     * shrinks mechanically as the lag grows — biasing the peak toward shorter
     * periods.  Dividing by the term count removes that bias and makes the peak
     * land on the true period, which is the main driver of precision.
     */
    private static double autocorrAt(double[] p, int lag) {
        int terms = p.length - lag;
        if (terms <= 0) return Double.NEGATIVE_INFINITY;
        double acc = 0;
        for (int y = 0; y < terms; y++)
            acc += p[y] * p[y + lag];
        return acc / terms;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase — windowed grid-energy minimisation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Gap phase in [0, {@code T}) that minimises the windowed projection energy
     * at every grid line.  A dense fixed-resolution scan (10 samples per pixel)
     * locates the coarse minimum, then a parabola through its two neighbours
     * gives a sub-pixel estimate.  A dense scan over a periodic — and therefore
     * not unimodal — objective is far more reliable than golden-section search,
     * which can latch onto a local dip.
     */
    static double findBestPhase(double[] proj, double T) {
        final int sub = 10;                 // sub-pixel resolution of the scan
        int steps = Math.max(1, (int) Math.round(T * sub));

        double bestEnergy = Double.MAX_VALUE;
        int bestStep = 0;
        double[] energies = new double[steps];
        for (int s = 0; s < steps; s++) {
            double phase = (double) s / sub;
            double e = gridEnergy(proj, T, phase);
            energies[s] = e;
            if (e < bestEnergy) {
                bestEnergy = e;
                bestStep = s;
            }
        }

        // Parabolic interpolation around the minimum (phase is periodic mod steps).
        int sm = (bestStep - 1 + steps) % steps;
        int sp = (bestStep + 1) % steps;
        double em = energies[sm], e0 = energies[bestStep], ep = energies[sp];
        double denom = 2 * (em - 2 * e0 + ep);
        double delta = Math.abs(denom) > 1e-9 ? (em - ep) / denom : 0;
        if (delta < -1 || delta > 1) delta = 0;

        double phase = (bestStep + delta) / sub;
        phase = ((phase % T) + T) % T;
        return phase;
    }

    /**
     * Mean projection over a small window centred on each grid line.  The phase
     * marks inter-line (or inter-char) gaps, so the true phase minimises this
     * energy.  Averaging over a ±halfWin window (rather than one pixel) absorbs
     * noise and keeps the objective smooth enough for stable sub-pixel
     * interpolation.
     */
    private static double gridEnergy(double[] proj, double T, double phase) {
        int n = proj.length;
        int halfWin = Math.max(0, (int) Math.round(T / 8.0));
        double sum = 0;
        int count = 0;
        for (double pos = phase; pos < n; pos += T) {
            for (int dy = -halfWin; dy <= halfWin; dy++) {
                double sp = pos + dy;
                int y0 = (int) Math.floor(sp);
                int y1 = y0 + 1;
                if (y0 < 0 || y1 >= n) continue;
                double frac = sp - y0;
                sum += (1 - frac) * proj[y0] + frac * proj[y1];
                count++;
            }
        }
        return count > 0 ? sum / count : Double.MAX_VALUE;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Cross-frame smoothing
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Exponential moving average.  When {@code prev} is uninitialised
     * ({@code < 0}) the current value is adopted as-is.
     */
    static double smooth(double prev, double current, double alpha) {
        if (prev < 0) return current;
        return alpha * current + (1 - alpha) * prev;
    }

    /**
     * Circular exponential moving average of a phase in [0, {@code period}).
     * The update follows the shortest way around the ring so the average does
     * not lurch when the phase wraps past 0.
     */
    static double smoothPhase(double prev, double current, double period, double alpha) {
        if (prev < 0) return current;
        double delta = current - prev;
        delta -= period * Math.round(delta / period);
        return (prev + alpha * delta + period) % period;
    }
}
