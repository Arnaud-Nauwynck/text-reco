package fr.an.textreco.model;

public enum GridDetectorMode {
    VALLEY,       // histogram valley + Hough (GridDetectorProcessor)
    CORRELATION   // autocorrelation + phase-locked grid (CorrelationGridDetectorProcessor)
}
