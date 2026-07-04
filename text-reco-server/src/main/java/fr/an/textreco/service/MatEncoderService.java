package fr.an.textreco.service;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Encodes an OpenCV Mat to a base64 PNG string on demand.
 * Called only when a REST request arrives — no per-frame encoding.
 */
@Component
public class MatEncoderService {

    public String matToBase64Png(Mat mat) {
        if (mat == null || mat.empty()) return null;
        MatOfByte buf = new MatOfByte();
        Imgcodecs.imencode(".png", mat, buf);
        byte[] bytes = buf.toArray();
        buf.release();
        return Base64.getEncoder().encodeToString(bytes);
    }
}
