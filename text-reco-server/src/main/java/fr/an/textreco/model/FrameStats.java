package fr.an.textreco.model;

public record FrameStats(
        int width,
        int height,
        double fps,
        long captureMs,
        long rawConvertMs,
        long edgeProcessMs,
        long edgeConvertMs,
        long perspProcessMs,
        long perspConvertMs,
        long totalMs,
        long tessOcrMs
) {
}
