package fr.an.textreco.util;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class FxImageUtils {

    /**
     * Converts a WritableImage to a single-channel (greyscale) OpenCV Mat.
     * Caller is responsible for releasing the returned Mat.
     */
    public static Mat writableImageToGreyMat(WritableImage img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        byte[] buf = new byte[w * h * 4]; // ARGB
        img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getByteBgraInstance(), buf, 0, w * 4);
        // Convert BGRA bytes → greyscale: simple luminance average of B,G,R
        byte[] grey = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            int b = buf[i * 4]     & 0xFF;
            int g = buf[i * 4 + 1] & 0xFF;
            int r = buf[i * 4 + 2] & 0xFF;
            grey[i] = (byte) ((r * 77 + g * 150 + b * 29) >> 8); // BT.601 luminance
        }
        Mat mat = new Mat(h, w, CvType.CV_8UC1);
        mat.put(0, 0, grey);
        return mat;
    }

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
