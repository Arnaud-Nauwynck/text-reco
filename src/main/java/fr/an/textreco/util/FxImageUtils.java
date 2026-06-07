package fr.an.textreco.util;

import javafx.scene.image.PixelFormat;
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
        // Convert BGRA bytes → greyscale: BT.601 luminance
        byte[] grey = new byte[w * h];
        for (int i = 0; i < w * h; i++) {
            int b = buf[i * 4] & 0xFF;
            int g = buf[i * 4 + 1] & 0xFF;
            int r = buf[i * 4 + 2] & 0xFF;
            grey[i] = (byte) ((r * 77 + g * 150 + b * 29) >> 8);
        }
        Mat mat = new Mat(h, w, CvType.CV_8UC1);
        MatTracker.alloc(mat, "writableImageToGreyMat");
        mat.put(0, 0, grey);
        return mat;
    }

    /**
     * Reusable buffer that avoids per-frame heap allocation.
     *
     * <p>Holds a single reused {@link WritableImage} (plus a scratch Mat and
     * pixel byte array) that are reallocated only when dimensions change.
     * The same {@code WritableImage} instance is returned on every call;
     * callers must not hold it across frames.
     */
    public static final class ImageBuffer {
        private final Mat rgbMat = MatFacade.alloc("ImageBuffer.rgbMat");
        private WritableImage image;
        private byte[] pixels;
        private int lastW = -1;
        private int lastH = -1;

        public ImageBuffer() {
        }

        /**
         * Converts {@code srcMat} to an RGB WritableImage on the background thread.
         * Accepts 1-channel (grey/binary) or 3-channel (BGR) input.
         * Reuses the same WritableImage instance; only reallocates when dimensions change.
         */
        public WritableImage update(Mat srcMat) {
            if (srcMat.channels() == 1)
                Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_GRAY2RGB);
            else
                Imgproc.cvtColor(srcMat, rgbMat, Imgproc.COLOR_BGR2RGB);
            int w = rgbMat.cols();
            int h = rgbMat.rows();
            if (w != lastW || h != lastH) {
                pixels = new byte[w * h * 3];
                image = new WritableImage(w, h);
                lastW = w;
                lastH = h;
            }
            rgbMat.get(0, 0, pixels);
            image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getByteRgbInstance(), pixels, 0, w * 3);
            return image;
        }

        public void release() {
            MatFacade.release(rgbMat, "ImageBuffer.rgbMat");
        }
    }
}
