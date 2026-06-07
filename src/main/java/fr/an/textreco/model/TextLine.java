package fr.an.textreco.model;

import javafx.scene.image.WritableImage;

/**
 * A single detected text line: its vertical span within the warped frame and
 * a cropped image of that strip.
 */
public record TextLine(
        int rowStart,
        int rowEnd,
        WritableImage lineImage
) {
    public int height() {
        return rowEnd - rowStart;
    }
}
