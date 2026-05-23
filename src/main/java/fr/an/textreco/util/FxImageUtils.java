package fr.an.textreco.util;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class FxImageUtils {

    /**
     * Reusable buffer that avoids per-frame heap allocation.
     * Holds the RGB scratch Mat, the pixel byte[], and the WritableImage.
     * All three are reallocated only when the frame dimensions change.
     */
    public static final class ImageBuffer {
        private final Mat rgbMat = new Mat();
        private byte[]        pixels;
        private int lastW = -1;
        private int lastH = -1;

        /**
         * Converts bgrMat to RGB bytes on the background thread, then returns a
         * fresh WritableImage each call so the returned image is never shared with
         * a previous scene-graph reference while being written.
         */
        public WritableImage update(Mat bgrMat) {
            Imgproc.cvtColor(bgrMat, rgbMat, Imgproc.COLOR_BGR2RGB);
            int w = rgbMat.cols();
            int h = rgbMat.rows();
            if (w != lastW || h != lastH) {
                pixels = new byte[w * h * 3];
                lastW  = w;
                lastH  = h;
            }
            rgbMat.get(0, 0, pixels);
            WritableImage image = new WritableImage(w, h);
            image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteRgbInstance(), pixels, 0, w * 3);
            return image;
        }

        public void release() {
            rgbMat.release();
        }
    }
}
