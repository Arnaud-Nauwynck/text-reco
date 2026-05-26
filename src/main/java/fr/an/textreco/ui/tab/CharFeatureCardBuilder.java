package fr.an.textreco.ui.tab;

import fr.an.textreco.processing.PreComputedFeaturesChar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * Static factory for the per-character feature card used in both
 * {@link CharFeaturesView} and {@link CharClassifierView}.
 *
 * <p>Each card contains:
 * <ul>
 *   <li>Template image (zoomed 4×) with green bounding-rect overlay and
 *       dashed symmetry-axis lines (cyan = vertical, yellow = horizontal)</li>
 *   <li>Horizontal histogram to the left (cyan horizontal bars, one per bbox row)</li>
 *   <li>Vertical histogram below (orange vertical bars, one per bbox column)</li>
 *   <li>Large glyph label</li>
 *   <li>Bounding-rect dimensions + symmetry badge</li>
 *   <li>Hu-moment bar chart (7 bars, teal/orange)</li>
 *   <li>Hu-moment numeric values (2 per line)</li>
 * </ul>
 */
public final class CharFeatureCardBuilder {

    // zoom factor applied to the template Mat pixels for display
    public static final int TMPL_ZOOM  = 4;

    private static final int BAR_W    = 120;
    private static final int BAR_H    = 56;
    private static final int HHIST_W  = 56;   // horizontal-hist bar max length
    private static final int VHIST_H  = 40;   // vertical-hist bar max height

    private CharFeatureCardBuilder() {}

    // -------------------------------------------------------------------------
    // public entry point
    // -------------------------------------------------------------------------

    /**
     * Builds and returns a self-contained card {@link VBox} for the given
     * character and its pre-computed features.
     */
    public static VBox buildCard(char ch, PreComputedFeaturesChar feat) {
        int dispW = feat.tmpl().cols() * TMPL_ZOOM;
        int dispH = feat.tmpl().rows() * TMPL_ZOOM;

        // template image
        ImageView tmplView = new ImageView(matToImage(feat.tmpl()));
        tmplView.setSmooth(false);
        tmplView.setFitWidth(dispW);
        tmplView.setFitHeight(dispH);

        // overlay: bounding rect + symmetry axes
        Canvas overlay = buildTemplateOverlay(feat, dispW, dispH);
        overlay.setMouseTransparent(true);

        StackPane tmplStack = new StackPane(tmplView, overlay);
        tmplStack.setAlignment(Pos.TOP_LEFT);
        tmplStack.setMaxSize(dispW, dispH);
        tmplStack.setMinSize(dispW, dispH);

        // h-histogram (left, horizontal bars = rows)
        Canvas hHistCanvas = buildHHistCanvas(feat);

        // v-histogram (below, vertical bars = columns)
        Canvas vHistCanvas = buildVHistCanvas(feat, dispW);

        HBox tmplRow = new HBox(3, hHistCanvas, tmplStack);
        tmplRow.setAlignment(Pos.TOP_LEFT);

        // glyph label
        Label glyphLabel = new Label(String.valueOf(ch));
        glyphLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
        glyphLabel.setStyle("-fx-text-fill: #88ff88;");
        glyphLabel.setAlignment(Pos.CENTER);
        glyphLabel.setMaxWidth(Double.MAX_VALUE);

        // bbox + symmetry info
        Rect bb = feat.boundingRect();
        Label infoLabel = new Label(String.format("bbox %dx%d @(%d,%d)  %s",
                bb.width, bb.height, bb.x, bb.y, symmetryText(feat)));
        infoLabel.setStyle("-fx-text-fill: #ffdd88; -fx-font-family: monospace; -fx-font-size: 10;");
        infoLabel.setAlignment(Pos.CENTER);
        infoLabel.setMaxWidth(Double.MAX_VALUE);

        // Hu bar chart + numeric values
        Canvas barChart = buildBarChart(feat.hu());
        Label  huLabel  = new Label(formatHu(feat.hu()));
        huLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-family: monospace; -fx-font-size: 10;");

        VBox card = new VBox(3, tmplRow, vHistCanvas, glyphLabel, infoLabel, barChart, huLabel);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(5));
        card.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        return card;
    }

    // -------------------------------------------------------------------------
    // template overlay
    // -------------------------------------------------------------------------

    private static Canvas buildTemplateOverlay(PreComputedFeaturesChar feat, int w, int h) {
        Canvas c = new Canvas(w, h);
        GraphicsContext gc = c.getGraphicsContext2D();

        // green bounding rect
        Rect bb = feat.boundingRect();
        gc.setStroke(Color.rgb(80, 255, 80, 0.75));
        gc.setLineWidth(1.0);
        gc.setLineDashes();
        gc.strokeRect(bb.x * TMPL_ZOOM, bb.y * TMPL_ZOOM,
                      bb.width * TMPL_ZOOM, bb.height * TMPL_ZOOM);

        // dashed symmetry axes through centroid
        gc.setLineWidth(1.5);
        gc.setLineDashes(4, 3);
        double cx = feat.centroidX() * TMPL_ZOOM;
        double cy = feat.centroidY() * TMPL_ZOOM;

        if (feat.hasVerticalSymmetry()) {
            gc.setStroke(Color.rgb(0, 220, 255, 0.85));
            gc.strokeLine(cx, 0, cx, h);
        }
        if (feat.hasHorizontalSymmetry()) {
            gc.setStroke(Color.rgb(255, 220, 0, 0.85));
            gc.strokeLine(0, cy, w, cy);
        }
        return c;
    }

    // -------------------------------------------------------------------------
    // histograms
    // -------------------------------------------------------------------------

    /** Horizontal histogram: horizontal bars, one per bbox row. Left of template. */
    private static Canvas buildHHistCanvas(PreComputedFeaturesChar feat) {
        float[] hist  = feat.hHist();
        int     dispH = feat.boundingRect().height * TMPL_ZOOM;
        Canvas c = new Canvas(HHIST_W, Math.max(1, dispH));
        if (hist == null || hist.length == 0 || dispH == 0) return c;

        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Color.rgb(15, 15, 15));
        gc.fillRect(0, 0, HHIST_W, dispH);

        double barSlot = (double) dispH / hist.length;
        for (int i = 0; i < hist.length; i++) {
            double bw = hist[i] * (HHIST_W - 1);
            double y  = i * barSlot;
            gc.setFill(Color.rgb(100, 200, 255, 0.85));
            gc.fillRect(0, y, bw, Math.max(1, barSlot - 1));
        }
        return c;
    }

    /** Vertical histogram: vertical bars, one per bbox column. Below template. */
    private static Canvas buildVHistCanvas(PreComputedFeaturesChar feat, int tmplDispW) {
        float[] hist  = feat.vHist();
        int     dispW = feat.boundingRect().width * TMPL_ZOOM;
        Canvas c = new Canvas(tmplDispW, VHIST_H);
        if (hist == null || hist.length == 0 || dispW == 0) return c;

        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Color.rgb(15, 15, 15));
        gc.fillRect(0, 0, tmplDispW, VHIST_H);

        double xOff    = feat.boundingRect().x * TMPL_ZOOM;
        double barSlot = (double) dispW / hist.length;
        for (int i = 0; i < hist.length; i++) {
            double bh = hist[i] * (VHIST_H - 2);
            double x  = xOff + i * barSlot;
            gc.setFill(Color.rgb(255, 180, 80, 0.85));
            gc.fillRect(x, VHIST_H - bh, Math.max(1, barSlot - 1), bh);
        }
        return c;
    }

    // -------------------------------------------------------------------------
    // Hu bar chart
    // -------------------------------------------------------------------------

    private static Canvas buildBarChart(double[] hu) {
        Canvas c = new Canvas(BAR_W, BAR_H);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Color.rgb(15, 15, 15));
        gc.fillRect(0, 0, BAR_W, BAR_H);
        if (hu == null || hu.length < 7) return c;

        double maxAbs = 1.0;
        for (double v : hu) if (Math.abs(v) > maxAbs) maxAbs = Math.abs(v);

        double barW = (BAR_W - 2) / 7.0;
        double midY = BAR_H / 2.0;

        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(0.5);
        gc.setLineDashes();
        gc.strokeLine(0, midY, BAR_W, midY);

        for (int i = 0; i < 7; i++) {
            double x    = 1 + i * barW;
            double norm = hu[i] / maxAbs;
            double bh   = Math.abs(norm) * (midY - 2);
            double y    = norm >= 0 ? midY - bh : midY;
            gc.setFill(norm >= 0 ? Color.rgb(60, 200, 180, 0.9) : Color.rgb(255, 140, 60, 0.9));
            gc.fillRect(x + 1, y, barW - 2, bh);
            gc.setFill(Color.rgb(160, 160, 160));
            gc.setFont(Font.font(7));
            gc.fillText(String.valueOf(i + 1), x + barW / 2.0 - 2, BAR_H - 1);
        }
        return c;
    }

    // -------------------------------------------------------------------------
    // text helpers
    // -------------------------------------------------------------------------

    public static String symmetryText(PreComputedFeaturesChar feat) {
        boolean v = feat.hasVerticalSymmetry();
        boolean h = feat.hasHorizontalSymmetry();
        if (v && h) return "⟺↕ V+H";
        if (v)      return "⟺  V";
        if (h)      return " ↕  H";
        return          "    ~";
    }

    public static String formatHu(double[] hu) {
        if (hu == null || hu.length < 7) return "n/a";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(String.format("h%d=%7.3f", i + 1, hu[i]));
            sb.append(i % 2 == 1 ? '\n' : "  ");
        }
        return sb.toString().stripTrailing();
    }

    /** Converts a greyscale CV_8UC1 {@link Mat} to a JavaFX {@link Image}. */
    public static Image matToImage(Mat mat) {
        int w = mat.cols(), h = mat.rows();
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        byte[] buf = new byte[w * h];
        mat.get(0, 0, buf);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = buf[y * w + x] & 0xFF;
                pw.setColor(x, y, Color.rgb(v, v, v));
            }
        return img;
    }
}
