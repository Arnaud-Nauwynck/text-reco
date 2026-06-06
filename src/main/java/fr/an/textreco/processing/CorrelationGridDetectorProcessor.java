package fr.an.textreco.processing;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.model.CorrelationGridDetectorSettings;
import lombok.Getter;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust terminal line detector using:
 *  1. Horizontal projection histogram
 *  2. Autocorrelation to find line height
 *  3. Phase-locked grid fitting for stable, precise positions
 *  4. Exponential smoothing across frames
 */
public class CorrelationGridDetectorProcessor {

    @Getter
    private final CorrelationGridDetectorSettings settings;

    private double smoothedLineHeight = -1;
    private double smoothedPhase      = -1;

    public CorrelationGridDetectorProcessor(CorrelationGridDetectorSettings settings) {
        this.settings = settings;
    }

    public CorrelationGridDetectionResult process(Mat frame) {
        int minH  = settings.minLineHeight.get();
        int maxH  = settings.maxLineHeight.get();
        double alpha = settings.smoothingAlpha.get();

        double[] proj = buildProjection(frame);

        double lineHeight = estimateLineHeightAutocorr(proj, minH, maxH);
        if (lineHeight < 0) {
            return new CorrelationGridDetectionResult(
                    frame.cols(), frame.rows(),
                    smoothedLineHeight < 0 ? 0 : smoothedLineHeight,
                    smoothedPhase < 0 ? 0 : smoothedPhase,
                    proj,
                    new ArrayList<>());
        }

        if (smoothedLineHeight < 0) smoothedLineHeight = lineHeight;
        else smoothedLineHeight = alpha * lineHeight + (1 - alpha) * smoothedLineHeight;

        double phase = findBestPhase(proj, smoothedLineHeight);

        if (smoothedPhase < 0) {
            smoothedPhase = phase;
        } else {
            double delta = phase - smoothedPhase;
            double H = smoothedLineHeight;
            delta -= H * Math.round(delta / H);
            smoothedPhase = (smoothedPhase + alpha * delta + H) % H;
        }

        List<Integer> positions = gridPositions(smoothedPhase, smoothedLineHeight, frame.rows());
        return new CorrelationGridDetectionResult(
                frame.cols(), frame.rows(),
                smoothedLineHeight, smoothedPhase,
                proj, positions);
    }

    /** Reset smoothing state (e.g. on source switch). */
    public void reset() {
        smoothedLineHeight = -1;
        smoothedPhase      = -1;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Step 1 — Horizontal projection
    // ────────────────────────────────────────────────────────────────────────

    private static double[] buildProjection(Mat frame) {
        Mat gray = new Mat();
        if (frame.channels() == 3)
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        else
            gray = frame.clone();

        Core.bitwise_not(gray, gray);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 1));
        Imgproc.erode(gray, gray, kernel);

        int usableCols = Math.max(gray.cols() - 15, gray.cols() / 2);
        Mat roi = gray.colRange(0, usableCols);

        int rows = roi.rows();
        double[] proj = new double[rows];
        for (int y = 0; y < rows; y++) {
            double sum = 0;
            for (int x = 0; x < usableCols; x++)
                sum += roi.get(y, x)[0];
            proj[y] = sum;
        }

        gray.release();
        return proj;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Step 2 — Autocorrelation → dominant line height
    // ────────────────────────────────────────────────────────────────────────

    private static double estimateLineHeightAutocorr(double[] proj, int minH, int maxH) {
        int n = proj.length;

        double mean = 0;
        for (double v : proj) mean += v;
        mean /= n;
        double[] p = new double[n];
        for (int i = 0; i < n; i++) p[i] = proj[i] - mean;

        double bestVal = Double.NEGATIVE_INFINITY;
        int    bestLag = -1;

        for (int lag = minH; lag <= maxH && lag < n; lag++) {
            double acc = 0;
            for (int y = 0; y < n - lag; y++)
                acc += p[y] * p[y + lag];
            if (acc > bestVal) {
                bestVal = acc;
                bestLag = lag;
            }
        }

        if (bestLag < 0) return -1;

        if (bestLag > minH && bestLag < maxH && bestLag < n - 1) {
            double vm = autocorrAt(p, bestLag - 1);
            double v0 = autocorrAt(p, bestLag);
            double vp = autocorrAt(p, bestLag + 1);
            double denom = 2 * (2 * v0 - vm - vp);
            if (Math.abs(denom) > 1e-6)
                return bestLag + (vm - vp) / denom;
        }
        return bestLag;
    }

    private static double autocorrAt(double[] p, int lag) {
        double acc = 0;
        for (int y = 0; y < p.length - lag; y++)
            acc += p[y] * p[y + lag];
        return acc;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Step 3 — Phase grid fitting
    // ────────────────────────────────────────────────────────────────────────

    private static double findBestPhase(double[] proj, double H) {
        int n = proj.length;
        int steps = (int) Math.ceil(H);

        double bestSum   = Double.MAX_VALUE;
        double bestPhase = 0;

        for (int s = 0; s < steps; s++) {
            double sum = 0;
            int count = 0;
            for (double pos = s; pos < n; pos += H) {
                int y = (int) Math.round(pos);
                if (y >= 0 && y < n) { sum += proj[y]; count++; }
            }
            if (count > 0) sum /= count;
            if (sum < bestSum) { bestSum = sum; bestPhase = s; }
        }

        bestPhase = goldenSectionMin(proj, H,
                Math.max(0, bestPhase - 1),
                Math.min(H - 1e-6, bestPhase + 1));
        return bestPhase;
    }

    private static double goldenSectionMin(double[] proj, double H, double lo, double hi) {
        final double PHI = (Math.sqrt(5) - 1) / 2;
        double a = lo, b = hi;
        double c = b - PHI * (b - a);
        double d = a + PHI * (b - a);
        for (int iter = 0; iter < 30 && (b - a) > 1e-4; iter++) {
            if (gridEnergy(proj, H, c) < gridEnergy(proj, H, d))
                b = d;
            else
                a = c;
            c = b - PHI * (b - a);
            d = a + PHI * (b - a);
        }
        return (a + b) / 2.0;
    }

    private static double gridEnergy(double[] proj, double H, double phase) {
        int n = proj.length;
        double sum = 0; int count = 0;
        for (double pos = phase; pos < n; pos += H) {
            int y0 = (int) pos;
            int y1 = Math.min(y0 + 1, n - 1);
            double frac = pos - y0;
            sum += (1 - frac) * proj[y0] + frac * proj[y1];
            count++;
        }
        return count > 0 ? sum / count : Double.MAX_VALUE;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Step 4 — Generate output positions
    // ────────────────────────────────────────────────────────────────────────

    private static List<Integer> gridPositions(double phase, double H, int height) {
        List<Integer> positions = new ArrayList<>();
        double textStart = phase + H * 0.5;
        double first = textStart % H;
        if (first < 0) first += H;
        for (double y = first; y < height; y += H)
            positions.add((int) Math.round(y));
        return positions;
    }
}
