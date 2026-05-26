package fr.an.textreco.processing;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds and owns the pre-computed feature set for every character in the
 * charset.  This is the "database" side of the recogniser: it renders
 * templates via AWT, computes moments, histograms, bounding rects and
 * symmetry flags once at startup, and exposes them for both classification
 * and visualisation.
 *
 * <p>This class has no classify logic.  Pass it to {@link CharTemplateClassifier}
 * to perform recognition.
 *
 * @see CharTemplateClassifier
 */
public class CharTemplateDb {

    public static final String CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    // -------------------------------------------------------------------------
    // PreComputedFeaturesChar record
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // fields
    // -------------------------------------------------------------------------

    private final int templateW;
    private final int templateH;

    private final Map<Character, PreComputedFeaturesChar> charFeatures = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // construction
    // -------------------------------------------------------------------------

    public CharTemplateDb(int templateW, int templateH) {
        this.templateW = templateW;
        this.templateH = templateH;
        buildTemplates();
    }

    public CharTemplateDb() {
        this(15, 28);
    }

    // -------------------------------------------------------------------------
    // public accessors
    // -------------------------------------------------------------------------

    public int getTemplateW() { return templateW; }
    public int getTemplateH() { return templateH; }

    /** Read-only view of the pre-computed per-character features. */
    public Map<Character, PreComputedFeaturesChar> getCharFeatures() {
        return Collections.unmodifiableMap(charFeatures);
    }

    public void release() {
        charFeatures.values().forEach(f -> f.tmpl().release());
        charFeatures.clear();
    }

    // -------------------------------------------------------------------------
    // public static feature-computation helpers
    // (also used by CharTemplateClassifier to extract features from live crops)
    // -------------------------------------------------------------------------

    /**
     * Returns the 7 log-scaled Hu moments for the given greyscale image.
     * The log scaling {@code sign(h)·log10(|h|)} is the standard normalisation
     * recommended by OpenCV so that all seven values have comparable magnitude.
     */
    public static double[] computeHuMoments(Mat grey8u) {
        Moments m  = Imgproc.moments(grey8u, false);
        Mat huMat  = new Mat();
        Imgproc.HuMoments(m, huMat);          // 7×1 CV_64F
        double[] hu = new double[7];
        for (int i = 0; i < 7; i++) {
            double v = huMat.get(i, 0)[0];
            hu[i] = (v == 0.0) ? 0.0 : Math.signum(v) * Math.log10(Math.abs(v));
        }
        huMat.release();
        return hu;
    }

    /**
     * Computes a normalised central-moment feature vector from a greyscale image.
     * Feature layout (8 values):
     * <pre>
     *   [0] mu20 / m00^2    — normalised horizontal variance
     *   [1] mu02 / m00^2    — normalised vertical   variance
     *   [2] mu11 / m00^2    — normalised cross-moment (tilt)
     *   [3] mu30 / m00^2.5  — horizontal skewness
     *   [4] mu03 / m00^2.5  — vertical   skewness
     *   [5] mu21 / m00^2.5  — mixed skewness
     *   [6] mu12 / m00^2.5  — mixed skewness
     *   [7] cx / width      — relative centroid x (≈ 0.5 for centred glyphs)
     * </pre>
     */
    public static double[] computeMomentFeatures(Mat grey8u) {
        return computeMomentFeaturesFromMoments(Imgproc.moments(grey8u, false), grey8u.cols());
    }

    // -------------------------------------------------------------------------
    // private — template rendering
    // -------------------------------------------------------------------------

    private void buildTemplates() {
        int renderW = templateW * 2;
        int renderH = templateH * 2;
        Font font   = chooseBestFont(renderH);

        for (char ch : CHARSET.toCharArray()) {
            Mat tmpl  = renderChar(ch, font, renderW, renderH);
            Mat small = new Mat();
            Imgproc.resize(tmpl, small, new Size(templateW, templateH), 0, 0, Imgproc.INTER_AREA);
            tmpl.release();
            charFeatures.put(ch, buildCharFeatures(small));
        }
    }

    /**
     * Computes all pre-computed features for a single greyscale template Mat in
     * one pass (single {@link Imgproc#moments} call).
     */
    private static PreComputedFeaturesChar buildCharFeatures(Mat small) {
        Moments m = Imgproc.moments(small, false);

        // centroid in pixel coords
        double cx = (m.m00 > 0) ? m.m10 / m.m00 : small.cols() / 2.0;
        double cy = (m.m00 > 0) ? m.m01 / m.m00 : small.rows() / 2.0;

        // Symmetry: pixel-level comparison with flipped copy — robust to font hinting.
        // flipCode: 1 = flip left↔right (vertical axis), 0 = flip top↔bottom (horizontal axis)
        boolean vertSym  = flipSymmetryScore(small, 1) < PreComputedFeaturesChar.SYMMETRY_THRESHOLD;
        boolean horizSym = flipSymmetryScore(small, 0) < PreComputedFeaturesChar.SYMMETRY_THRESHOLD;

        // Hu moments (log-scaled)
        Mat huMat = new Mat();
        Imgproc.HuMoments(m, huMat);
        double[] hu = new double[7];
        for (int i = 0; i < 7; i++) {
            double v = huMat.get(i, 0)[0];
            hu[i] = (v == 0.0) ? 0.0 : Math.signum(v) * Math.log10(Math.abs(v));
        }
        huMat.release();

        // central-moment feature vector
        double[] moment = computeMomentFeaturesFromMoments(m, small.cols());

        // bounding rect of non-zero pixels (threshold at 16/255 to skip noise)
        Mat binary     = new Mat();
        Mat nonZeroPts = new Mat();
        Imgproc.threshold(small, binary, 16, 255, Imgproc.THRESH_BINARY);
        Core.findNonZero(binary, nonZeroPts);
        Rect bbox = nonZeroPts.empty()
                ? new Rect(0, 0, small.cols(), small.rows())
                : Imgproc.boundingRect(nonZeroPts);
        nonZeroPts.release();
        binary.release();

        // histograms within the bounding rect
        Mat roi    = small.submat(bbox);
        float[] hHist = rowSums(roi, bbox.width);    // length = bbox.height
        float[] vHist = colSums(roi, bbox.height);   // length = bbox.width

        return new PreComputedFeaturesChar(small, hu, moment, vertSym, horizSym, cx, cy, bbox, hHist, vHist);
    }

    // -------------------------------------------------------------------------
    // private static helpers
    // -------------------------------------------------------------------------

    private static double[] computeMomentFeaturesFromMoments(Moments m, int width) {
        double m00 = m.m00;
        if (m00 == 0) return new double[8];

        double area2  = m00 * m00;
        double area25 = Math.pow(m00, 2.5);

        double[] f = new double[8];
        f[0] = m.mu20 / area2;
        f[1] = m.mu02 / area2;
        f[2] = m.mu11 / area2;
        f[3] = m.mu30 / area25;
        f[4] = m.mu03 / area25;
        f[5] = m.mu21 / area25;
        f[6] = m.mu12 / area25;
        f[7] = (width > 0) ? (m.m10 / m00) / width : 0.5;
        return f;
    }

    /**
     * Normalised mean absolute difference between {@code src} and its flip.
     * Score 0 = perfectly symmetric; 1 = completely opposite.
     *
     * @param flipCode 1 = left↔right, 0 = top↔bottom
     */
    private static double flipSymmetryScore(Mat src, int flipCode) {
        Mat flipped = new Mat();
        Core.flip(src, flipped, flipCode);
        Mat diff = new Mat();
        Core.absdiff(src, flipped, diff);
        double mad = Core.sumElems(diff).val[0] / (255.0 * src.rows() * src.cols());
        diff.release();
        flipped.release();
        return mad;
    }

    /** Per-row pixel sums of {@code roi}, normalised to [0,1] by width×255. */
    private static float[] rowSums(Mat roi, int width) {
        int h = roi.rows();
        float[] hist = new float[h];
        float norm = width * 255f;
        if (norm == 0) return hist;
        for (int r = 0; r < h; r++)
            hist[r] = (float) (Core.sumElems(roi.row(r)).val[0] / norm);
        return hist;
    }

    /** Per-column pixel sums of {@code roi}, normalised to [0,1] by height×255. */
    private static float[] colSums(Mat roi, int height) {
        int w = roi.cols();
        float[] hist = new float[w];
        float norm = height * 255f;
        if (norm == 0) return hist;
        for (int c = 0; c < w; c++)
            hist[c] = (float) (Core.sumElems(roi.col(c)).val[0] / norm);
        return hist;
    }

    private static Font chooseBestFont(int renderH) {
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

        byte[] pixels = new byte[w * h];
        int[]  raw    = new int[w * h];
        img.getRaster().getPixels(0, 0, w, h, raw);
        for (int i = 0; i < raw.length; i++) pixels[i] = (byte) raw[i];
        Mat mat = new Mat(h, w, CvType.CV_8UC1);
        mat.put(0, 0, pixels);
        return mat;
    }
}
