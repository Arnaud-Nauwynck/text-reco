package fr.an.textreco.ui.tab;

import fr.an.textreco.processing.CharTemplateClassifier;
import fr.an.textreco.processing.CharTemplateClassifier.PreComputedFeaturesChar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;
import org.opencv.core.Mat;

import java.util.Map;

/**
 * Tab that displays the pre-computed Hu moment features for every character
 * in the classifier's template set.
 *
 * <p>Layout: a scrollable {@link FlowPane} where each card shows:
 * <ul>
 *   <li>The rendered template image</li>
 *   <li>The character glyph</li>
 *   <li>All 7 log-scaled Hu moment values</li>
 *   <li>A small bar chart of the 7 values</li>
 * </ul>
 */
public class CharFeaturesView {

    private static final int   TMPL_ZOOM    = 4;     // zoom factor for template preview
    private static final int   BAR_W        = 120;
    private static final int   BAR_H        = 56;
    private static final int   CARD_SPACING = 6;

    @Getter
    private final ScrollPane root;

    public CharFeaturesView(CharTemplateClassifier classifier) {
        FlowPane flow = new FlowPane(CARD_SPACING, CARD_SPACING);
        flow.setPadding(new Insets(10));
        flow.setStyle("-fx-background-color: #1e1e1e;");

        Map<Character, PreComputedFeaturesChar> features = classifier.getCharFeatures();
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            flow.getChildren().add(buildCard(e.getKey(), e.getValue()));
        }

        root = new ScrollPane(flow);
        root.setFitToWidth(true);
        root.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
    }

    // -------------------------------------------------------------------------
    // card builder
    // -------------------------------------------------------------------------

    private VBox buildCard(char ch, PreComputedFeaturesChar feat) {
        // --- template image preview ---
        ImageView tmplView = new ImageView(matToImage(feat.tmpl()));
        tmplView.setSmooth(false);
        tmplView.setFitWidth(feat.tmpl().cols() * TMPL_ZOOM);
        tmplView.setFitHeight(feat.tmpl().rows() * TMPL_ZOOM);

        // --- glyph label ---
        Label glyphLabel = new Label(String.valueOf(ch));
        glyphLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 22));
        glyphLabel.setStyle("-fx-text-fill: #88ff88;");
        glyphLabel.setAlignment(Pos.CENTER);
        glyphLabel.setMaxWidth(Double.MAX_VALUE);

        // --- Hu moment values ---
        Label huLabel = new Label(formatHu(feat.hu()));
        huLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-family: monospace; -fx-font-size: 10;");

        // --- bar chart ---
        Canvas barChart = buildBarChart(feat.hu());

        VBox card = new VBox(3, tmplView, glyphLabel, barChart, huLabel);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(5));
        card.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        return card;
    }

    // -------------------------------------------------------------------------
    // bar chart for 7 Hu values
    // -------------------------------------------------------------------------

    private static Canvas buildBarChart(double[] hu) {
        Canvas c = new Canvas(BAR_W, BAR_H);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.setFill(Color.rgb(15, 15, 15));
        gc.fillRect(0, 0, BAR_W, BAR_H);

        if (hu == null || hu.length < 7) return c;

        // find absolute max for scaling (always at least 1 to avoid /0)
        double maxAbs = 1.0;
        for (double v : hu) if (Math.abs(v) > maxAbs) maxAbs = Math.abs(v);

        double barW   = (BAR_W - 2) / 7.0;
        double midY   = BAR_H / 2.0;

        // zero line
        gc.setStroke(Color.rgb(80, 80, 80));
        gc.setLineWidth(0.5);
        gc.strokeLine(0, midY, BAR_W, midY);

        for (int i = 0; i < 7; i++) {
            double x   = 1 + i * barW;
            double norm = hu[i] / maxAbs;            // –1 .. +1
            double barH = Math.abs(norm) * (midY - 2);
            double y   = norm >= 0 ? midY - barH : midY;

            // colour: positive = teal, negative = orange
            Color fill = norm >= 0
                    ? Color.rgb(60, 200, 180, 0.9)
                    : Color.rgb(255, 140, 60, 0.9);
            gc.setFill(fill);
            gc.fillRect(x + 1, y, barW - 2, barH);

            // index label at the bottom
            gc.setFill(Color.rgb(160, 160, 160));
            gc.setFont(javafx.scene.text.Font.font(7));
            gc.fillText(String.valueOf(i + 1), x + barW / 2.0 - 2, BAR_H - 1);
        }
        return c;
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a greyscale OpenCV {@link Mat} (CV_8UC1) to a JavaFX {@link Image}.
     */
    private static Image matToImage(Mat mat) {
        int w = mat.cols();
        int h = mat.rows();
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        byte[] buf = new byte[w * h];
        mat.get(0, 0, buf);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = buf[y * w + x] & 0xFF;
                pw.setColor(x, y, Color.rgb(v, v, v));
            }
        }
        return img;
    }

    /** Formats 7 Hu values as compact two-per-line text. */
    private static String formatHu(double[] hu) {
        if (hu == null || hu.length < 7) return "n/a";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(String.format("h%d=%7.3f", i + 1, hu[i]));
            sb.append(i % 2 == 1 ? '\n' : "  ");
        }
        return sb.toString().stripTrailing();
    }
}
