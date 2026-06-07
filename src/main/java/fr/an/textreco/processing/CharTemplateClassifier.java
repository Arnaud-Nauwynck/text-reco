package fr.an.textreco.processing;

import fr.an.textreco.util.MatFacade;
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
 *   <li>{@link #classifyByHuMoments}   — weighted L1 distance on log-scaled Hu moments</li>
 *   <li>{@link #classifyByMoments}     — weighted L2 on normalised central moments</li>
 * </ul>
 *
 * <h3>Hu moment weights</h3>
 * <p>The seven log-scaled Hu moments have very different discriminative power at
 * the small template sizes used here:
 * <ul>
 *   <li>h1 (I1 = μ20+μ02) — overall "mass spread", highly stable → high weight</li>
 *   <li>h2 (I2 = (μ20−μ02)²+4μ11²) — aspect-ratio skew → high weight</li>
 *   <li>h3–h4 — third-order, moderately useful at typical font sizes</li>
 *   <li>h5–h7 — fourth/higher-order, noisy at small sizes → lower weight</li>
 * </ul>
 * Default weights are {@link #DEFAULT_HU_WEIGHTS}; override via
 * {@link #setHuWeights(double[])}.
 *
 * <p>This class owns only the scratch {@link Mat} objects needed during
 * classification; all pre-computed template data lives in {@link CharTemplateDb}.
 */
public class CharTemplateClassifier {

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * One entry in the top-3 ranking: a candidate character and its
     * template-matching score (TM_CCOEFF_NORMED, higher = better).
     */
    public record TopEntry(char ch, float score) {
    }

    /**
     * Classification result.
     *
     * @param ch    best character, or {@code '?'} when below the confidence threshold
     * @param score TM_CCOEFF_NORMED score of the best match (range ≈ −1..1)
     * @param top3  up to 3 best candidates sorted by score descending;
     *              index 0 is always the best (same as {@code ch}/{@code score})
     */
    public record Result(char ch, float score, TopEntry[] top3) {
        public boolean isConfident() {
            return ch != '?';
        }

        @Override
        public String toString() {
            return ch == '?' ? "? (low conf)" : ch + " (" + String.format("%.2f", score) + ")";
        }
    }

    // -------------------------------------------------------------------------
    // constants
    // -------------------------------------------------------------------------

    /**
     * Default per-moment weights for {@link #classifyByHuMoments}.
     * <pre>
     *   index  Hu invariant   default weight  rationale
     *   -----  ------------   --------------  ---------
     *     0    h1 (I1)             5.0        very stable; encodes overall spread
     *     1    h2 (I2)             4.0        encodes aspect-ratio asymmetry
     *     2    h3 (I3)             3.0        third-order; useful for open vs. closed
     *     3    h4 (I4)             2.0        moderate reliability at small sizes
     *     4    h5 (I5)             1.5        noisy at small sizes
     *     5    h6 (I6)             1.0        least reliable
     *     6    h7 (I7)             0.5        sign-sensitive skew; often near zero
     * </pre>
     */
    public static final double[] DEFAULT_HU_WEIGHTS = {5.0, 4.0, 3.0, 2.0, 1.5, 1.0, 0.5};

    // -------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------

    private final CharTemplateDb db;
    private final float confidenceThreshold;

    /**
     * Per-moment weights used by {@link #classifyByHuMoments}.
     * Must have length ≥ 7.  Copied on set to prevent external mutation.
     */
    private double[] huWeights = DEFAULT_HU_WEIGHTS.clone();

    /**
     * Scratch Mats — reused across calls to avoid allocation pressure.
     */
    private final Mat matchResult = MatFacade.alloc("CharClassifier.matchResult");
    private final Mat resized = MatFacade.alloc("CharClassifier.resized");
    private final Mat grey = MatFacade.alloc("CharClassifier.grey");

    // -------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------

    public CharTemplateClassifier(CharTemplateDb db, float confidenceThreshold) {
        this.db = db;
        this.confidenceThreshold = confidenceThreshold;
    }

    public CharTemplateClassifier(CharTemplateDb db) {
        this(db, 0.1f);
    }

    // -------------------------------------------------------------------------
    // accessors
    // -------------------------------------------------------------------------

    public CharTemplateDb getDb() {
        return db;
    }

    /**
     * Returns a defensive copy of the current Hu-moment weight vector (length 7).
     */
    public double[] getHuWeights() {
        return huWeights.clone();
    }

    /**
     * Replaces the Hu-moment weight vector used by {@link #classifyByHuMoments}.
     *
     * @param weights array of length ≥ 7; a defensive copy is stored
     * @throws IllegalArgumentException if {@code weights} has fewer than 7 elements
     */
    public void setHuWeights(double[] weights) {
        if (weights == null || weights.length < 7)
            throw new IllegalArgumentException("huWeights must have at least 7 elements");
        this.huWeights = weights.clone();
    }

    // -------------------------------------------------------------------------
    // classify methods
    // -------------------------------------------------------------------------

    /**
     * Classifies {@code charCrop} by normalised cross-correlation template matching
     * (TM_CCOEFF_NORMED).  The crop is resized to the template dimensions before
     * matching.
     *
     * <p>The returned {@link Result} always carries the top-3 candidates (fewer if
     * the charset has fewer than 3 characters) sorted by score descending.
     */
    public Result classify(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f, new TopEntry[0]);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f, new TopEntry[0]);

        toGrey(charCrop);   // writes into this.grey
        Imgproc.resize(grey, resized, new Size(db.getTemplateW(), db.getTemplateH()),
                0, 0, Imgproc.INTER_LINEAR);

        // track top-3 inline: top[0] = best, top[1] = 2nd, top[2] = 3rd
        char[] topCh = {'?', '?', '?'};
        float[] topScore = {-2f, -2f, -2f};

        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            Imgproc.matchTemplate(resized, e.getValue().tmpl(), matchResult, Imgproc.TM_CCOEFF_NORMED);
            float score = (float) matchResult.get(0, 0)[0];
            // insert into top-3 (insertion sort on 3 elements)
            if (score > topScore[0]) {
                topScore[2] = topScore[1];
                topCh[2] = topCh[1];
                topScore[1] = topScore[0];
                topCh[1] = topCh[0];
                topScore[0] = score;
                topCh[0] = e.getKey();
            } else if (score > topScore[1]) {
                topScore[2] = topScore[1];
                topCh[2] = topCh[1];
                topScore[1] = score;
                topCh[1] = e.getKey();
            } else if (score > topScore[2]) {
                topScore[2] = score;
                topCh[2] = e.getKey();
            }
        }

        int count = 0;
        for (float s : topScore) if (s > -2f) count++;
        TopEntry[] top3 = new TopEntry[count];
        for (int i = 0; i < count; i++) top3[i] = new TopEntry(topCh[i], topScore[i]);

        char bestCh = top3.length > 0 ? top3[0].ch() : '?';
        float bestScore = top3.length > 0 ? top3[0].score() : 0f;
        return bestScore >= confidenceThreshold
                ? new Result(bestCh, bestScore, top3)
                : new Result('?', bestScore, top3);
    }

    /**
     * Classifies {@code charCrop} by L1 distance on log-scaled Hu moments.
     * Hu moments are rotation-, scale- and translation-invariant, so no resize is needed.
     */
    public Result classifyByHuMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f, new TopEntry[0]);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f, new TopEntry[0]);

        double[] cropHu = CharTemplateDb.computeHuMoments(toGreyRef(charCrop));

        char bestCh = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            double dist = huDistance(cropHu, e.getValue().hu(), huWeights);
            if (dist < bestDist) {
                bestDist = dist;
                bestCh = e.getKey();
            }
        }

        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold
                ? new Result(bestCh, score, new TopEntry[0])
                : new Result('?', score, new TopEntry[0]);
    }

    /**
     * Classifies {@code charCrop} by weighted L2 distance on normalised central moments.
     * The centroid-based normalisation makes the comparison translation-invariant.
     */
    public Result classifyByMoments(Mat charCrop) {
        if (charCrop == null || charCrop.empty()) return new Result('?', 0f, new TopEntry[0]);
        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        if (features.isEmpty()) return new Result('?', 0f, new TopEntry[0]);

        double[] feat = CharTemplateDb.computeMomentFeatures(toGreyRef(charCrop));

        char bestCh = '?';
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            double dist = momentDistance(feat, e.getValue().moment());
            if (dist < bestDist) {
                bestDist = dist;
                bestCh = e.getKey();
            }
        }

        float score = (float) (1.0 / (1.0 + bestDist));
        return score >= confidenceThreshold
                ? new Result(bestCh, score, new TopEntry[0])
                : new Result('?', score, new TopEntry[0]);
    }

    // -------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------

    public void release() {
        MatFacade.release(matchResult, "CharClassifier.matchResult");
        MatFacade.release(resized, "CharClassifier.resized");
        MatFacade.release(grey, "CharClassifier.grey");
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
        if (src.channels() == 1) {
            // copyTo throws when grey already holds a crop of a different total
            // size (it does not reallocate a non-empty destination). create()
            // reshapes the existing native buffer in place — no Mat allocation —
            // and is a no-op when the size/type already match.
            grey.create(src.rows(), src.cols(), src.type());
            src.copyTo(grey);
        } else if (src.channels() == 3) {
            Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);
        } else {
            Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGRA2GRAY);
        }
    }

    /**
     * Returns a greyscale reference for {@code src}: returns {@code src} directly
     * if it is already single-channel, otherwise converts into the scratch
     * {@link #grey} Mat and returns that.
     */
    private Mat toGreyRef(Mat src) {
        if (src.channels() == 1) return src;
        if (src.channels() == 3) Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGR2GRAY);
        else Imgproc.cvtColor(src, grey, Imgproc.COLOR_BGRA2GRAY);
        return grey;
    }

    /**
     * Weighted L1 distance between two log-scaled Hu moment vectors.
     *
     * @param a       crop Hu moments (length ≥ 7)
     * @param b       template Hu moments (length ≥ 7)
     * @param weights per-moment weights (length ≥ 7); index 0 → h1, …, index 6 → h7
     */
    private static double huDistance(double[] a, double[] b, double[] weights) {
        double sum = 0;
        for (int i = 0; i < 7; i++) sum += weights[i] * Math.abs(a[i] - b[i]);
        return sum;
    }

    /**
     * Weighted L2 distance between two normalised central-moment feature vectors.
     */
    private static double momentDistance(double[] a, double[] b) {
        // Variance features (indices 0-1) are most discriminative at low resolution.
        double[] w = {10.0, 10.0, 5.0, 2.0, 2.0, 1.0, 1.0, 3.0};
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += w[i] * d * d;
        }
        return Math.sqrt(sum);
    }
}
