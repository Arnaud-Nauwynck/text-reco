package fr.an.textreco.processing;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * All pre-computed data for a single template character.
 *
 * @param tmpl                  greyscale OpenCV Mat at final template size
 * @param hu                    7-value log-scaled Hu moment array
 * @param moment                8-value normalised central-moment feature vector
 * @param hasVerticalSymmetry   true when the glyph is left-right symmetric
 * @param hasHorizontalSymmetry true when the glyph is top-bottom symmetric
 * @param centroidX             centroid x in pixel coordinates (within tmpl)
 * @param centroidY             centroid y in pixel coordinates (within tmpl)
 * @param boundingRect          tight bounding box of non-zero pixels in tmpl
 * @param hHist                 horizontal histogram: per-row pixel sum normalised
 *                              to [0,1], length = boundingRect.height
 * @param vHist                 vertical histogram: per-column pixel sum normalised
 *                              to [0,1], length = boundingRect.width
 */
public record PreComputedFeaturesChar(
        Mat tmpl,
        double[] hu,
        double[] moment,
        boolean hasVerticalSymmetry,
        boolean hasHorizontalSymmetry,
        double centroidX,
        double centroidY,
        Rect boundingRect,
        float[] hHist,
        float[] vHist) {

    /**
     * Threshold for the normalised mean-absolute-difference between the image
     * and its flip (range 0–1, where 0 = perfectly symmetric).
     * 0.10 = allow up to 10 % average pixel difference, tolerating font
     * hinting and anti-aliasing artefacts while still rejecting asymmetric
     * glyphs like N, F, J, etc.
     */
    static final double SYMMETRY_THRESHOLD = 0.10;
}
