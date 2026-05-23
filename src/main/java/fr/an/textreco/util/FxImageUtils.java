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
        private WritableImage image;
        private int lastW = -1;
        private int lastH = -1;

        public WritableImage update(Mat bgrMat) {
            Imgproc.cvtColor(bgrMat, rgbMat, Imgproc.COLOR_BGR2RGB);
            int w = rgbMat.cols();
            int h = rgbMat.rows();
            if (w != lastW || h != lastH) {
                pixels = new byte[w * h * 3];
                image  = new WritableImage(w, h);
                lastW  = w;
                lastH  = h;
            }
            rgbMat.get(0, 0, pixels);
            image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteRgbInstance(), pixels, 0, w * 3);
            return image;
        }

        public void release() {
            rgbMat.release();
        }
    }
}
