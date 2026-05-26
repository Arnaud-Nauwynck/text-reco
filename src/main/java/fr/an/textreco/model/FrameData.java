package fr.an.textreco.model;

import org.opencv.core.Mat;

public class FrameData {

    public final Mat raw = new Mat();

    public final Mat gray = new Mat();

    public final Mat processed = new Mat();

    public FrameData() {
    }

    public void release() {
        raw.release();
        gray.release();
        processed.release();
    }
}