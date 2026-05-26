package fr.an.textreco.processing;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Moments;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recognises a single pre-segmented character crop against the base64 charset
 * using OpenCV template matching (TM_CCOEFF_NORMED).
 *
 * Templates are rendered at startup via AWT at the configured pixel size.
 * No training data or external dependencies required beyond OpenCV.
 */
public class CharTemplateClassifier {

    public static final String CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    private final int templateW;
    private final int templateH;
    private final float confidenceThreshold;

    /**
     * All pre-computed data for a single template character.
     *
     * @param tmpl     greyscale OpenCV Mat at final template size
     * @param hu       7-value log-scaled Hu moment array
     * @param moment   8-value normalised central-moment feature vector
     */
    public record PreComputedFeaturesChar(Mat tmpl, double[] hu, double[] moment) {}

    private final Map<Character, PreComputedFeaturesChar> charFeatures = new LinkedHashMap<>();

    /** Read-only view of the pre-computed per-character features (templates + moments). */
    public Map<Character, PreComputedFeaturesChar> getCharFeatures() {
        return java.util.Collections.unmodifiableMap(charFeatures);
    }
    private final Mat matchResult = new Mat();
    private final Mat resized     = new Mat();
    private final Mat grey        = new Mat();

    public CharTemplateClassifier(int templateW, int templateH, float confidenceThreshold) {
        this.templateW           = templateW;
        this.templateH           = templateH;
        this.confidenceThreshold = confidenceThreshold;
        buildTemplates();
    }

    public CharTemplateClassifier() {
        this(15, 28, 0.1f);
    }

    // -------------------------------------------------------------------------
    // public API
    // -------------------------------------------------------------------------

    public record Result(char ch, float score) {
        public boolean isConfident() { return ch != '?'; }
        @Override public String toString() {
            return ch == '?' ? "? (low conf)" : ch + " (" + String.format("%.2f", score) + ")";
        }
    }

    public Result classify(Mat charCrop) {
        if (charCrop == null || charCrop.empty() || charFeatures.isEmpty()) return new Result('?', 0f);

        // Convert to greyscale
        if (charCrop.channels() == 3) {
            Imgproc.cvtColor(charCrop, grey, Imgproc.COLOR_BGR2GRAY);
        } else if (charCrop.channels() == 4) {
            Imgproc.cvtColor(charCrop, grey, Imgproc.COLOR_BGRA2GRAY);
        } else {
            charCrop.copyTo(grey);
        }

        // Resize to template dimensions
        Imgproc.resize(grey, resized, new Size(templateW, templateH), 0, 0, Imgproc.INTER_LINEAR);

        char bestCh    = '?';
        float bestScore = -1f;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : charFeatures.entrySet()) {
            Imgproc.matchTemplate(resized, e.getValue().tmpl(), matchResult, Imgproc.TM_CCOEFF_NORMED);
            float score = (float) matchResult.get(0, 0)[0];
            if (score > bestScore) { bestScore = score; bestCh = e.getKey(); }
        }
        return bestScore >= confidenceThreshold ? new Result(bestCh, bestScore) : new Result('?', bestScore);
    }

    /**
     * Classify {@code charCrop} using the L1 distance between log-scaled Hu moments.
     * <p>
     * Hu moments are rotation-, scale-, and translation-invariant, making them
     * robust to small size variations without needing a precise resize step.
     * The log scaling {@code sign(h)·log10(|h|)} is the standard normalisation
     * recommended by OpenCV so that all seven values have comparable magnitude.
     *
     * @param charCrop  grey or colour single-character image crop
     * @return          best-matching character with its inverse-distance score
     */
    public Result classifyByHuMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty() || charFeatures.isEmpty()) return new Result('?', 0f);

        double[] cropHu = computeHuMoments(toGrey(charCrop));

        char   bestCh   = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : charFeatures.entrySet()) {
            double dist = huDistance(cropHu, e.getValue().hu());
            if (dist < bestDist) { bestDist = dist; bestCh = e.getKey(); }
        }

        // Convert distance to a [0,1] score: closer → higher score.
        // A distance of 0 means perfect match; we use a soft inversion.
        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold ? new Result(bestCh, score) : new Result('?', score);
    }

    /**
     * Classify {@code charCrop} by comparing a set of normalised central moments
     * (mass, variance, skewness, cross-moment) against the pre-computed template
     * moment vectors.
     * <p>
     * The crop is first <em>centred via its centroid</em> so that translation
     * differences (caused by slightly off-centre segmentation) do not pollute the
     * comparison.  The feature vector is then scaled to the image area so that
     * different crop sizes are comparable.
     *
     * @param charCrop grey or colour single-character image crop
     * @return best-matching character with its inverse-distance score
     */
    public Result classifyByMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty() || charFeatures.isEmpty()) return new Result('?', 0f);

        double[] feat = computeMomentFeatures(toGrey(charCrop));

        char   bestCh   = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : charFeatures.entrySet()) {
            double dist = momentDistance(feat, e.getValue().moment());
            if (dist < bestDist) { bestDist = dist; bestCh = e.getKey(); }
        }

        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold ? new Result(bestCh, score) : new Result('?', score);
    }

    /**
     * Returns the 7 log-scaled Hu moments for the given greyscale image.
     * The log scaling is {@code sign(h)·log10(|h|+ε)} so that zero moments
     * stay at 0 and the sign of the original moment is preserved.
     */
    public static double[] computeHuMoments(Mat grey8u) {
        Moments m   = Imgproc.moments(grey8u, false);
        Mat     huMat = new Mat();
        Imgproc.HuMoments(m, huMat);          // 7×1 CV_64F

        double[] hu = new double[7];
        for (int i = 0; i < 7; i++) {
            double v = huMat.get(i, 0)[0];
            // log scale: sign(v) * log10(|v|)  (|v| clamped away from 0)
            hu[i] = (v == 0.0) ? 0.0 : Math.signum(v) * Math.log10(Math.abs(v));
        }
        huMat.release();
        return hu;
    }

    /**
     * Computes a normalised central-moment feature vector from a greyscale image.
     * <p>
     * Steps:
     * <ol>
     *   <li>Compute raw moments to find the centroid (cx, cy).</li>
     *   <li>Translate the image so the centroid sits at the geometric centre —
     *       achieved here analytically via the central moments that OpenCV already
     *       provides (mu_pq = sum of (x-cx)^p*(y-cy)^q*I(x,y)).</li>
     *   <li>Normalise by the image area (m00) to obtain scale-independent features.</li>
     * </ol>
     * Feature layout (8 values):
     * <pre>
     *   [0] mu20 / m00^2  — normalised horizontal variance (spread along x)
     *   [1] mu02 / m00^2  — normalised vertical   variance (spread along y)
     *   [2] mu11 / m00^2  — normalised cross-moment (orientation / tilt)
     *   [3] mu30 / m00^(5/2)  — horizontal skewness
     *   [4] mu03 / m00^(5/2)  — vertical   skewness
     *   [5] mu21 / m00^(5/2)  — mixed skewness
     *   [6] mu12 / m00^(5/2)  — mixed skewness
     *   [7] cx / width   — relative centroid x (translation test — should be ~0.5)
     * </pre>
     */
    public static double[] computeMomentFeatures(Mat grey8u) {
        Moments m = Imgproc.moments(grey8u, false);
        double  m00 = m.m00;
        if (m00 == 0) return new double[8];   // blank / empty patch

        double area2 = m00 * m00;
        double area25 = Math.pow(m00, 2.5);
        double w = grey8u.cols();

        double[] f = new double[8];
        f[0] = m.mu20 / area2;
        f[1] = m.mu02 / area2;
        f[2] = m.mu11 / area2;
        f[3] = m.mu30 / area25;
        f[4] = m.mu03 / area25;
        f[5] = m.mu21 / area25;
        f[6] = m.mu12 / area25;
        f[7] = (w > 0) ? (m.m10 / m00) / w : 0.5;   // centroid x fraction
        return f;
    }

    /** Converts a Mat to greyscale, reusing the scratch {@code grey} field when possible. */
    private Mat toGrey(Mat src) {
        if (src.channels() == 1) return src;
        if (src.channels() == 3) { Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);  return grey; }
        Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGRA2GRAY);
        return grey;
    }

    /** Weighted L2 distance between two moment feature vectors. */
    private static double momentDistance(double[] a, double[] b) {
        // Higher weight for variance features (most discriminative at low resolution),
        // lower for higher-order and the centroid.
        double[] w = { 10.0, 10.0, 5.0, 2.0, 2.0, 1.0, 1.0, 3.0 };
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += w[i] * d * d;
        }
        return Math.sqrt(sum);
    }

    /** L1 distance between two log-scaled Hu moment vectors. */
    private static double huDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < 7; i++) sum += Math.abs(a[i] - b[i]);
        return sum;
    }

    public void release() {
        charFeatures.values().forEach(f -> f.tmpl().release());
        charFeatures.clear();
        matchResult.release();
        resized.release();
        grey.release();
    }

    // -------------------------------------------------------------------------
    // template rendering
    // -------------------------------------------------------------------------

    private void buildTemplates() {
        // Render at 2× then downscale for better anti-alias quality
        int renderW = templateW * 2;
        int renderH = templateH * 2;

        Font font = chooseBestFont(renderH);

        for (char ch : CHARSET.toCharArray()) {
            Mat tmpl = renderChar(ch, font, renderW, renderH);
            // Downscale to final template size
            Mat small = new Mat();
            Imgproc.resize(tmpl, small, new Size(templateW, templateH), 0, 0, Imgproc.INTER_AREA);
            tmpl.release();
            charFeatures.put(ch, new PreComputedFeaturesChar(
                    small,
                    computeHuMoments(small),
                    computeMomentFeatures(small)));
        }
    }

    private static Font chooseBestFont(int renderH) {
        // Prefer a monospace font that matches a terminal; fall back to "Monospaced"
        float fontSize = renderH * 0.72f;
        for (String name : new String[]{"Consolas", "Courier New", "Lucida Console", "Monospaced"}) {
            Font f = new Font(name, Font.PLAIN, (int) fontSize);
            if (f.getFamily().equalsIgnoreCase(name) || name.equals("Monospaced")) return f;
        }
        return new Font("Monospaced", Font.PLAIN, (int) fontSize);
    }

    private static Mat renderChar(char ch, Font font, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);
        g.setFont(font);
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        int x = (w - fm.charWidth(ch)) / 2;
        int y = (h - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(String.valueOf(ch), x, y);
        g.dispose();

        // Convert BufferedImage → OpenCV Mat (greyscale)
        byte[] pixels = new byte[w * h];
        int[] raw = new int[w * h];
        img.getRaster().getPixels(0, 0, w, h, raw);
        for (int i = 0; i < raw.length; i++) pixels[i] = (byte) raw[i];
        Mat mat = new Mat(h, w, CvType.CV_8UC1);
        mat.put(0, 0, pixels);
        return mat;
    }
}
