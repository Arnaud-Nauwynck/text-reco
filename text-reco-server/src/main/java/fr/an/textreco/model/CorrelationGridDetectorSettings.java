package fr.an.textreco.model;

import lombok.Data;

@Data
public class CorrelationGridDetectorSettings {

    private volatile int minLineHeight = 8;
    private volatile int maxLineHeight = 40;
    private volatile int minCharWidth = 4;
    private volatile int maxCharWidth = 30;

    private volatile boolean forceLineHeight = false;
    private volatile double forcedLineHeight = 28.0;

    private volatile boolean forceCharWidth = false;
    private volatile double forcedCharWidth = 14.0;

    private volatile boolean forceLineOffset = false;
    private volatile double forcedLineOffset = 0.0;

    private volatile boolean forceColOffset = false;
    private volatile double forcedColOffset = 0.0;

    private volatile double smoothingAlpha = 0.15;
}
