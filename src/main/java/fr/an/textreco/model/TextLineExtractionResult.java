package fr.an.textreco.model;

import java.util.List;

public record TextLineExtractionResult(
        int frameWidth,
        int frameHeight,
        List<TextLine> lines,
        float[] rowSums,       // raw horizontal projection histogram, length == frameHeight
        float[] smoothedSums,  // smoothed histogram used for valley detection
        int[] valleys        // row indices of detected valley separators
) {
}
