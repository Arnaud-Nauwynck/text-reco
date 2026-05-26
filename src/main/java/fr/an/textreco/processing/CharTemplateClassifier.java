package fr.an.textreco.processing;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Map;

/**
 * Recognises a single pre-segmented character crop against the charset
 * held in a {@link CharTemplateDb}.
 *
 * <p>Three strategies are available:
 * <ul>
 *   <li>{@link #classify}              — template matching (TM_CCOEFF_NORMED)</li>
 *   <li>{@link #classifyByHuMoments}   — L1 distance on log-scaled Hu moments</li>
 *   <li>{@link #classifyByMoments}     — weighted L2 on normalised central moments</li>
 * </ul>
 *
 * <p>This class owns only the scratch {@link Mat} objects needed during
 * classification; all pre-computed template data lives in {@link CharTemplateDb}.
 */
public class CharTemplateClassifier {

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    public record Result(char ch, float score) {
        public boolean isConfident() { return ch != '?'; }

        @Override
        public String toString() {
            return ch == '?' ? "? (low conf)" : ch + " (" + String.format("%.2f", score) + ")";
        }
    }

    // -------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------

    private final CharTemplateDb db;
    private final float          confidenceThreshold;

    /** Scratch Mats — reused across calls to avoid allocation pressure. */
    private final Mat matchResult = new Mat();
    private final Mat resized     = new Mat();
    private final Mat grey        = new Mat();

    // -------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------

    public CharTemplateClassifier(CharTemplateDb db, float confidenceThreshold) {
        this.db                  = db;
        this.confidenceThreshold = confidenceThreshold;
    }

    public CharTemplateClassifier(CharTemplateDb db) {
        this(db, 0.1f);
    }

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    public CharTemplateDb getDb() { return db; }

    // -------------------------------------------------------------------------
    // classify methods
    // -------------------------------------------------------------------------

    /**
     * Classifies {@code charCrop} by normalised cross-correlation template matching
     * (TM_CCOEFF_NORMED).  The crop is resized to the template dimensions before
     * matching.
     */
    public Result classify(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f);

        toGrey(charCrop);   // writes into this.grey
        Imgproc.resize(grey, resized, new Size(db.getTemplateW(), db.getTemplateH()),
                0, 0, Imgproc.INTER_LINEAR);

        char  bestCh    = '?';
        float bestScore = -1f;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            Imgproc.matchTemplate(resized, e.getValue().tmpl(), matchResult, Imgproc.TM_CCOEFF_NORMED);
            float score = (float) matchResult.get(0, 0)[0];
            if (score > bestScore) { bestScore = score; bestCh = e.getKey(); }
        }
        return bestScore >= confidenceThreshold ? new Result(bestCh, bestScore) : new Result('?', bestScore);
    }

    /**
     * Classifies {@code charCrop} by L1 distance on log-scaled Hu moments.
     * Hu moments are rotation-, scale- and translation-invariant, so no resize is needed.
     */
    public Result classifyByHuMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f);

        double[] cropHu = CharTemplateDb.computeHuMoments(toGreyRef(charCrop));

        char   bestCh   = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            double dist = huDistance(cropHu, e.getValue().hu());
            if (dist < bestDist) { bestDist = dist; bestCh = e.getKey(); }
        }

        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold ? new Result(bestCh, score) : new Result('?', score);
    }

    /**
     * Classifies {@code charCrop} by weighted L2 distance on normalised central moments.
     * The centroid-based normalisation makes the comparison translation-invariant.
     */
    public Result classifyByMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f);

        double[] feat = CharTemplateDb.computeMomentFeatures(toGreyRef(charCrop));

        char   bestCh   = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            double dist = momentDistance(feat, e.getValue().moment());
            if (dist < bestDist) { bestDist = dist; bestCh = e.getKey(); }
        }

        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold ? new Result(bestCh, score) : new Result('?', score);
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    public void release() {
        matchResult.release();
        resized.release();
        grey.release();
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    /**
     * Converts {@code src} to greyscale into the scratch {@link #grey} Mat.
     * Always writes to {@code grey}; callers that need a reference use
     * {@link #toGreyRef}.
     */
    private void toGrey(Mat src) {
        if (src.channels() == 1)      src.copyTo(grey);
        else if (src.channels() == 3) Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);
        else                          Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGRA2GRAY);
    }

    /**
     * Returns a greyscale reference for {@code src}: returns {@code src} directly
     * if it is already single-channel, otherwise converts into the scratch
     * {@link #grey} Mat and returns that.
     */
    private Mat toGreyRef(Mat src) {
        if (src.channels() == 1) return src;
        if (src.channels() == 3) Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);
        else                     Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGRA2GRAY);
        return grey;
    }

    /** L1 distance between two log-scaled Hu moment vectors. */
    private static double huDistance(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < 7; i++) sum += Math.abs(a[i] - b[i]);
        return sum;
    }

    /** Weighted L2 distance between two normalised central-moment feature vectors. */
    private static double momentDistance(double[] a, double[] b) {
        // Variance features (indices 0-1) are most discriminative at low resolution.
        double[] w = { 10.0, 10.0, 5.0, 2.0, 2.0, 1.0, 1.0, 3.0 };
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += w[i] * d * d;
        }
        return Math.sqrt(sum);
    }
}
