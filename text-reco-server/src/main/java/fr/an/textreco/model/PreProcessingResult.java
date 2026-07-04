package fr.an.textreco.model;

/**
 * Scalar pre-processing outputs published after each pipeline iteration.
 * Image data (binary, morph Mats) lives in PreProcessingProcessor and is
 * encoded on-demand by the REST layer.
 */
public record PreProcessingResult(
        int frameWidth,
        int frameHeight,
        float[] hRowSums,
        float[] vColSums,
        int[] vValleys
) {
}
