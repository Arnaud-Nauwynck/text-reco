package fr.an.textreco.processing;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
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

    private final Map<Character, Mat> templates = new LinkedHashMap<>();
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
        this(15, 28, 0.55f);
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
        if (charCrop == null || charCrop.empty() || templates.isEmpty()) return new Result('?', 0f);

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
        for (Map.Entry<Character, Mat> e : templates.entrySet()) {
            Imgproc.matchTemplate(resized, e.getValue(), matchResult, Imgproc.TM_CCOEFF_NORMED);
            float score = (float) matchResult.get(0, 0)[0];
            if (score > bestScore) { bestScore = score; bestCh = e.getKey(); }
        }
        return bestScore >= confidenceThreshold ? new Result(bestCh, bestScore) : new Result('?', bestScore);
    }

    public void release() {
        templates.values().forEach(Mat::release);
        templates.clear();
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
            templates.put(ch, small);
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
