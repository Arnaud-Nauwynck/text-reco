package fr.an.textreco.model;

import lombok.Data;

@Data
public class GridDetectorSettings {

    private volatile int minLineH = 25;
    private volatile int maxLineH = 80;
    private volatile int minCharW = 8;
    private volatile int maxCharW = 60;

    private volatile boolean forceLineH = false;
    private volatile double forcedLineH = 28.0;

    private volatile boolean forceCharWidth = false;
    private volatile double forcedCharWRatio = 2.0;
    private volatile double forcedCharWPx = 15.0;

    private volatile boolean forceLineY0 = false;
    private volatile double forcedLineY0 = 0.0;

    private volatile boolean forceCharX0 = false;
    private volatile double forcedCharX0 = 0.0;

    private volatile boolean forceLineCount = false;
    private volatile int forcedLineCount = 24;

    private volatile boolean forceColCount = false;
    private volatile int forcedColCount = 80;
}
