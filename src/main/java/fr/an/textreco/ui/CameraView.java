package fr.an.textreco.ui;

import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import lombok.Getter;

public class CameraView {

    @Getter
    private final BorderPane root = new BorderPane();

    private final ImageView imageView = new ImageView();

    public CameraView() {
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(1000);
        root.setCenter(imageView);
    }

    public void setImage(Image image) {
        imageView.setImage(image);
    }

}