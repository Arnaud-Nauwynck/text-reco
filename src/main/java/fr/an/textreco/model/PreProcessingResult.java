package fr.an.textreco.model;

import javafx.scene.image.WritableImage;

/**
 * All pre-processing intermediates produced from the perspective-corrected frame.
 * Immutable snapshot published to the FX thread each frame.
 */
public record PreProcessingResult(
        int frameWidth,
        int frameHeight,

        WritableImage binaryImage,       // adaptive-threshold binary

        float[] hRowSums,                // horizontal projection: float[h], sum of lit pixels per row
        float[] vColSums,                // vertical   projection: float[w], sum of lit pixels per col

        WritableImage morphHoriz,        // morphological opening with horizontal line  ——
        WritableImage morphVert,         // morphological opening with vertical   line  |
        WritableImage morphDiagFwd,      // morphological opening with forward diagonal /
        WritableImage morphDiagBwd       // morphological opening with backward diagonal \
) {}
