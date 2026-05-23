package fr.an.textreco.model;

import java.util.List;

public record TextLineExtractionResult(
        int        frameWidth,
        int        frameHeight,
        List<TextLine> lines,
        float[]    rowSums       // horizontal projection histogram, length == frameHeight
) {}
