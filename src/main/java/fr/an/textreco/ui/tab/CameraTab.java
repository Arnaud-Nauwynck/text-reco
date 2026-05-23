package fr.an.textreco.ui.tab;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class CameraTab {

    @Getter
    private final HBox root = new HBox(8);

    private final ImageView rawImageView = new ImageView();
    private final ImageView processedImageView = new ImageView();

    public CameraTab() {
        configureImageView(rawImageView);
        configureImageView(processedImageView);
        root.getChildren().addAll(buildPanel("Camera", rawImageView), buildPanel("Processed", processedImageView));
        root.setPadding(new Insets(8));
        root.setAlignment(Pos.CENTER);
    }

    private void configureImageView(ImageView iv) {
        iv.setPreserveRatio(true);
        iv.setFitWidth(480);
        iv.setFitHeight(360);
    }

    private VBox buildPanel(String title, ImageView iv) {
        Label label = new Label(title);
        label.setFont(Font.font("System", FontWeight.BOLD, 14));
        label.setStyle("-fx-text-fill: #dddddd;");
        BorderPane frame = new BorderPane(iv);
        frame.setStyle("-fx-background-color: #2d2d2d; -fx-border-color: #555555; -fx-border-width: 1;");
        frame.setPadding(new Insets(4));
        VBox panel = new VBox(4, label, frame);
        panel.setAlignment(Pos.TOP_CENTER);
        return panel;
    }

    public void setRawImage(Image image) {
        rawImageView.setImage(image);
    }

    public void setProcessedImage(Image image) {
        processedImageView.setImage(image);
    }
}
