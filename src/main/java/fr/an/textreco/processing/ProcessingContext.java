package fr.an.textreco.processing;

import org.opencv.core.Mat;

public class ProcessingContext {

    public final Mat temp1 = new Mat();

    public final Mat temp2 = new Mat();

    public final Mat temp3 = new Mat();

    public void release() {
        temp1.release();
        temp2.release();
        temp3.release();
    }
}