package fr.an.textreco.util;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class FxImageUtils {

//    public static Image matToImage(Mat mat) {
//        return SwingFXUtils.toFXImage(matToBufferedImage(mat), null);
//    }

    // Convert OpenCV Mat → JavaFX Image
    public static WritableImage matToJavaFXWritableImage(Mat mat) {
        Mat converted = new Mat();
        Imgproc.cvtColor(mat, converted, Imgproc.COLOR_BGR2RGB);
        int width = converted.cols();
        int height = converted.rows();
        byte[] data = new byte[width * height * 3];
        converted.get(0, 0, data);
        WritableImage image = new WritableImage(width, height);
        PixelWriter pw = image.getPixelWriter();
        pw.setPixels(0, 0, width, height, PixelFormat.getByteRgbInstance(), data, 0, width * 3);
        return image;
    }

//    private static BufferedImage matToAwtBufferedImage(Mat mat) {
//        int type = mat.channels() == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR;
//        int bufferSize = mat.channels() * mat.cols() * mat.rows();
//        byte[] buffer = new byte[bufferSize];
//        mat.get(0, 0, buffer);
//        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
//        byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
//        System.arraycopy(buffer, 0, target, 0, buffer.length);
//        return image;
//    }
}