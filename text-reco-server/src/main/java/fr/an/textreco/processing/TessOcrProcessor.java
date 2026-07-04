package fr.an.textreco.processing;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

public class TessOcrProcessor {

    private static final String BASE64_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";

    private final Tesseract tess = new Tesseract();
    private volatile boolean ready = false;

    public TessOcrProcessor() {
        try {
            Path tessdata = extractBundledTessdata();
            tess.setDatapath(tessdata.toAbsolutePath().toString());
            // Use OSD traineddata (bundled, orientation-detection model) as a lightweight base.
            // We don't load a language model — char recognition is driven purely by the whitelist
            // and the LSTM weights embedded in eng.traineddata, with all dictionaries disabled.
            tess.setLanguage("eng");
            tess.setOcrEngineMode(1); // OEM_LSTM_ONLY
            tess.setPageSegMode(6);   // single uniform block of text
            tess.setVariable("tessedit_char_whitelist", BASE64_CHARS);
            tess.setVariable("load_system_dawg", "false");
            tess.setVariable("load_freq_dawg", "false");
            tess.setVariable("load_unambig_dawg", "false");
            tess.setVariable("load_punc_dawg", "false");
            tess.setVariable("load_number_dawg", "false");
            tess.setVariable("load_bigram_dawg", "false");
            tess.setVariable("language_model_penalty_non_dict_word", "0");
            tess.setVariable("language_model_penalty_non_freq_dict_word", "0");
            ready = true;
        } catch (IOException e) {
            System.err.println("[TessOCR] init failed: " + e.getMessage());
        }
    }

    public String recognize(Mat mat) {
        if (!ready || mat == null || mat.empty()) return "";
        try {
            MatOfByte mob = new MatOfByte();
            Imgcodecs.imencode(".png", mat, mob);
            byte[] bytes = mob.toArray();
            mob.release();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return "";
            return tess.doOCR(img).trim();
        } catch (TesseractException | IOException e) {
            return "[OCR error: " + e.getMessage() + "]";
        }
    }

    public void release() {
        ready = false;
    }

    // -------------------------------------------------------------------------
    // tessdata extraction
    // -------------------------------------------------------------------------

    private static Path extractBundledTessdata() throws IOException {
        Path tmpDir = Files.createTempDirectory("textreco-tessdata");
        tmpDir.toFile().deleteOnExit();

        copyResource("eng.traineddata", tmpDir.resolve("eng.traineddata"));

        return tmpDir;
    }

    private static void copyResource(String name, Path dest) throws IOException {
        // Try tess4j classloader first (works when jar is on unnamed module classpath)
        ClassLoader cl = Tesseract.class.getClassLoader();
        InputStream in = (cl != null) ? cl.getResourceAsStream("tessdata/" + name) : null;
        if (in == null) {
            // Fallback: open the tess4j jar directly via the code-source location
            URI jarUri;
            try {
                jarUri = Tesseract.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            } catch (URISyntaxException ex) {
                throw new IOException("Cannot locate tess4j jar", ex);
            }
            URI zipUri = URI.create("jar:" + jarUri);
            try (FileSystem fs = FileSystems.newFileSystem(zipUri, Map.of())) {
                Path entry = fs.getPath("tessdata", name);
                Files.copy(entry, dest);
                return;
            } catch (Exception e2) {
                throw new IOException("Bundled tessdata not found: " + name + " (" + e2.getMessage() + ")");
            }
        }
        try (in; OutputStream out = Files.newOutputStream(dest)) {
            in.transferTo(out);
        }
    }
}
