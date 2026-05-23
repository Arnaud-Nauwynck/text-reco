package fr.an.textreco.ui.tab;

import fr.an.textreco.processing.PerspectiveTransformProcessor;
import fr.an.textreco.ui.ProcessingPipeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

/**
 * Shows the raw camera frame with 4 draggable corner handles defining the
 * perspective quad, and a warped output preview beside it.
 */
public class PerspectiveTransformView {

    private static final double DISPLAY_W = 480;
    private static final double DISPLAY_H = 360;
    private static final double HANDLE_R  = 8;

    @Getter
    private final HBox root = new HBox(8);

    private final ImageView rawView      = new ImageView();
    private final Canvas    overlayCanvas = new Canvas(DISPLAY_W, DISPLAY_H);
    private final ImageView warpedView   = new ImageView();

    // corner positions in display-pixel space; order: TL, TR, BR, BL
    private final double[] cx = {DISPLAY_W * 0.1, DISPLAY_W * 0.9, DISPLAY_W * 0.9, DISPLAY_W * 0.1};
    private final double[] cy = {DISPLAY_H * 0.1, DISPLAY_H * 0.1, DISPLAY_H * 0.9, DISPLAY_H * 0.9};

    private final Circle[] handles = new Circle[4];
    private final String[] cornerLabels = {"TL", "TR", "BR", "BL"};

    private final PerspectiveTransformProcessor processor;

    // actual rendered image size (may differ from DISPLAY_W/H due to aspect ratio)
    private double renderedW = DISPLAY_W;
    private double renderedH = DISPLAY_H;
    private double offsetX   = 0;
    private double offsetY   = 0;

    public PerspectiveTransformView(ProcessingPipeline pipeline, PerspectiveTransformProcessor processor) {
        this.processor = processor;
        pipeline.getRawImageProperty()        .addListener((obs, o, img) -> setRawImage(img));
        pipeline.getPerspectiveImageProperty().addListener((obs, o, img) -> setWarpedImage(img));

        configureImageView(rawView);
        configureImageView(warpedView);

        Pane overlay = buildOverlay();
        StackPane rawStack = new StackPane(rawView, overlay);
        rawStack.setAlignment(Pos.TOP_LEFT);

        VBox rawPanel    = buildPanel("Source (drag corners)", rawStack);
        VBox warpedPanel = buildPanel("Warped output", warpedView);

        root.getChildren().addAll(rawPanel, warpedPanel);
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.CENTER);
    }

    private void configureImageView(ImageView iv) {
        iv.setPreserveRatio(true);
        iv.setFitWidth(DISPLAY_W);
        iv.setFitHeight(DISPLAY_H);
    }

    private Pane buildOverlay() {
        Pane pane = new Pane(overlayCanvas);
        pane.setPrefSize(DISPLAY_W, DISPLAY_H);
        pane.setMaxSize(DISPLAY_W, DISPLAY_H);
        overlayCanvas.setMouseTransparent(true);

        for (int i = 0; i < 4; i++) {
            final int idx = i;
            Circle c = new Circle(cx[i], cy[i], HANDLE_R);
            c.setFill(Color.rgb(255, 100, 0, 0.75));
            c.setStroke(Color.WHITE);
            c.setStrokeWidth(1.5);
            c.setCursor(Cursor.CROSSHAIR);

            // drag state
            double[] dragStart = new double[2];

            c.setOnMousePressed(e -> {
                dragStart[0] = e.getX();
                dragStart[1] = e.getY();
                e.consume();
            });
            c.setOnMouseDragged(e -> {
                double nx = cx[idx] + (e.getX() - dragStart[0]);
                double ny = cy[idx] + (e.getY() - dragStart[1]);
                nx = Math.max(0, Math.min(DISPLAY_W, nx));
                ny = Math.max(0, Math.min(DISPLAY_H, ny));
                cx[idx] = nx;
                cy[idx] = ny;
                dragStart[0] = e.getX();
                dragStart[1] = e.getY();
                c.setCenterX(nx);
                c.setCenterY(ny);
                pushCornersToProcessor();
                redrawOverlay();
                e.consume();
            });

            handles[i] = c;
            pane.getChildren().add(c);
        }

        redrawOverlay();
        return pane;
    }

    private void redrawOverlay() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, DISPLAY_W, DISPLAY_H);

        // quad outline
        gc.setStroke(Color.rgb(255, 200, 0, 0.9));
        gc.setLineWidth(1.5);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            gc.strokeLine(cx[i], cy[i], cx[j], cy[j]);
        }

        // corner labels
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        for (int i = 0; i < 4; i++) {
            gc.fillText(cornerLabels[i], cx[i] + HANDLE_R + 2, cy[i] - HANDLE_R - 2);
        }
    }

    private void pushCornersToProcessor() {
        double[] xRel = new double[4];
        double[] yRel = new double[4];
        for (int i = 0; i < 4; i++) {
            xRel[i] = renderedW > 0 ? (cx[i] - offsetX) / renderedW : cx[i] / DISPLAY_W;
            yRel[i] = renderedH > 0 ? (cy[i] - offsetY) / renderedH : cy[i] / DISPLAY_H;
            xRel[i] = Math.max(0, Math.min(1, xRel[i]));
            yRel[i] = Math.max(0, Math.min(1, yRel[i]));
        }
        processor.setCorners(xRel, yRel);
    }

    private VBox buildPanel(String title, javafx.scene.Node content) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 13));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555555; -fx-border-width: 1;");
        frame.setPadding(new Insets(4));
        VBox panel = new VBox(4, label, frame);
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }

    private void setRawImage(Image image) {
        rawView.setImage(image);
        if (image != null) {
            // compute actual rendered bounds inside the ImageView (preserveRatio)
            double iw = image.getWidth();
            double ih = image.getHeight();
            double scale = Math.min(DISPLAY_W / iw, DISPLAY_H / ih);
            renderedW = iw * scale;
            renderedH = ih * scale;
            offsetX   = (DISPLAY_W - renderedW) / 2.0;
            offsetY   = (DISPLAY_H - renderedH) / 2.0;
        }
    }

    private void setWarpedImage(Image image) {
        warpedView.setImage(image);
    }
}
