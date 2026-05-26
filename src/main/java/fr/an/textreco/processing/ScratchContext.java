package fr.an.textreco.processing;

import org.opencv.core.Mat;

/**
 * Scratch OpenCV Mats shared within a single processor.
 * Not to be confused with {@link fr.an.textreco.model.ProcessingContext}, which
 * is the MVC Model holding all observable pipeline outputs.
 */
public class ScratchContext {

    public final Mat temp1 = new Mat();
    public final Mat temp2 = new Mat();
    public final Mat temp3 = new Mat();

    /** scratch Mat for BGR→RGB color conversion before writing to JavaFX WritableImage */
    public final Mat rgbConvert = new Mat();

    public void release() {
        temp1.release();
        temp2.release();
        temp3.release();
        rgbConvert.release();
    }
}
