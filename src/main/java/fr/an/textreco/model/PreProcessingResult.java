package fr.an.textreco.model;

import javafx.scene.image.WritableImage;

/**
 * All pre-processing intermediates produced from the perspective-corrected frame.
 * Immutable snapshot published to the FX thread each frame.
 */
public record PreProcessingResult(
        int frameWidth,
        int frameHeight,

        WritableImage binaryImage,       // binarized binary

        float[] hRowSums,                // horizontal projection: float[h], sum (open+close) per row
        float[] vColSums,                // vertical   projection: float[w], sum (open+close) per col
        int[] vValleys,                // column indices of vertical valley separators (from vColSums)

        WritableImage morphHoriz,        // morphological opening  — (horiz)
        WritableImage morphVert,         // morphological opening  | (vert)
        WritableImage morphDiagFwd,      // morphological opening  / (diag fwd)
        WritableImage morphDiagBwd,      // morphological opening  \ (diag bwd)

        WritableImage closeHoriz,        // morphological closing  — (horiz)
        WritableImage closeVert,         // morphological closing  | (vert)
        WritableImage closeDiagFwd,      // morphological closing  / (diag fwd)
        WritableImage closeDiagBwd       // morphological closing  \ (diag bwd)
) {
}
