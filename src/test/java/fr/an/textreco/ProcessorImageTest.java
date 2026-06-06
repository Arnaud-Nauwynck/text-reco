package fr.an.textreco;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.model.CorrelationGridDetectorSettings;
import fr.an.textreco.model.GridDetectionResult;
import fr.an.textreco.model.GridDetectorSettings;
import fr.an.textreco.processing.CorrelationGridDetectorProcessor;
import fr.an.textreco.processing.GridDetectorProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that run the grid-detection processors against a real
 * terminal screenshot (36pt Consolas, white-on-dark, 17 lines of repeated chars).
 *
 * Expected grid for term36-consolas-base64-bis.png:
 *   lineH  ≈ 47–48 px   (36 pt Consolas at 96 dpi)
 *   charW  ≈ 21–22 px
 *   lines  = 17
 */
class ProcessorImageTest {

    // path relative to project root — Maven runs tests from the project root
    private static final String IMAGE_PATH =
            "src/test/term36-consolas-base64-bis.png";

    private static final double EXPECTED_LINE_H_MIN = 44.0;
    private static final double EXPECTED_LINE_H_MAX = 52.0;
    private static final double EXPECTED_CHAR_W_MIN = 18.0;
    private static final double EXPECTED_CHAR_W_MAX = 26.0;
    private static final int    EXPECTED_LINE_COUNT  = 17;
    private static final int    LINE_COUNT_TOLERANCE = 2;

    private static Mat bgrImage;
    private static Mat grayImage;
    private static float[] hRowSums;
    private static float[] vColSums;

    @BeforeAll
    static void loadImage() {
        nu.pattern.OpenCV.loadLocally();

        File file = new File(IMAGE_PATH);
        assertTrue(file.exists(), "Test image not found: " + file.getAbsolutePath());

        bgrImage = Imgcodecs.imread(file.getAbsolutePath());
        assertFalse(bgrImage.empty(), "imread returned empty Mat for " + IMAGE_PATH);

        int w = bgrImage.cols();
        int h = bgrImage.rows();

        // binarise: Otsu on inverted grey (white text → white foreground)
        grayImage = new Mat();
        Imgproc.cvtColor(bgrImage, grayImage, Imgproc.COLOR_BGR2GRAY);
        Mat binary = new Mat();
        Imgproc.threshold(grayImage, binary, 0, 255,
                Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);

        // horizontal structuring element — same half-len as default settings (7)
        Mat seHoriz = Mat.ones(1, 15, CvType.CV_8U);
        Mat seVert  = Mat.ones(15, 1, CvType.CV_8U);

        Mat morphH = new Mat();
        Mat morphV = new Mat();
        Mat closeH = new Mat();
        Mat closeV = new Mat();
        Imgproc.morphologyEx(binary, morphH, Imgproc.MORPH_OPEN,  seHoriz);
        Imgproc.morphologyEx(binary, morphV, Imgproc.MORPH_OPEN,  seVert);
        Imgproc.morphologyEx(binary, closeH, Imgproc.MORPH_CLOSE, seHoriz);
        Imgproc.morphologyEx(binary, closeV, Imgproc.MORPH_CLOSE, seVert);

        // row sums: add horiz open + close, reduce along cols
        Mat histH = new Mat();
        Mat histV = new Mat();
        Core.add(morphH, closeH, histH);
        Core.add(morphV, closeV, histV);

        Mat rowSumMat = new Mat();
        Mat colSumMat = new Mat();
        Core.reduce(histH, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);
        Core.reduce(histV, colSumMat, 0, Core.REDUCE_SUM, CvType.CV_32F);

        hRowSums = new float[h];
        vColSums = new float[w];
        rowSumMat.get(0, 0, hRowSums);
        colSumMat.get(0, 0, vColSums);

        for (Mat m : List.of(binary, seHoriz, seVert, morphH, morphV, closeH, closeV,
                              histH, histV, rowSumMat, colSumMat)) {
            m.release();
        }
    }

    // -------------------------------------------------------------------------
    // GridDetectorProcessor (valley + Hough)
    // -------------------------------------------------------------------------

    @Test
    void valleyDetector_lineHeight_isInExpectedRange() {
        GridDetectorSettings settings = new GridDetectorSettings();
        settings.minLineH.set(30);
        settings.maxLineH.set(70);
        settings.minCharW.set(12);
        settings.maxCharW.set(40);

        GridDetectorProcessor proc = new GridDetectorProcessor(settings);
        GridDetectionResult result = proc.processFromSums(
                hRowSums, bgrImage.rows(), vColSums, bgrImage.cols());

        assertNotNull(result, "GridDetectionResult must not be null");
        assertTrue(result.bestLineH() >= EXPECTED_LINE_H_MIN
                        && result.bestLineH() <= EXPECTED_LINE_H_MAX,
                String.format("bestLineH=%.2f not in [%.0f, %.0f]",
                        result.bestLineH(), EXPECTED_LINE_H_MIN, EXPECTED_LINE_H_MAX));

        proc.release();
    }

    @Test
    void valleyDetector_charWidth_isInExpectedRange() {
        GridDetectorSettings settings = new GridDetectorSettings();
        settings.minLineH.set(30);
        settings.maxLineH.set(70);
        settings.minCharW.set(12);
        settings.maxCharW.set(40);

        GridDetectorProcessor proc = new GridDetectorProcessor(settings);
        GridDetectionResult result = proc.processFromSums(
                hRowSums, bgrImage.rows(), vColSums, bgrImage.cols());

        assertNotNull(result);
        assertTrue(result.bestCharW() >= EXPECTED_CHAR_W_MIN
                        && result.bestCharW() <= EXPECTED_CHAR_W_MAX,
                String.format("bestCharW=%.2f not in [%.0f, %.0f]",
                        result.bestCharW(), EXPECTED_CHAR_W_MIN, EXPECTED_CHAR_W_MAX));

        proc.release();
    }

    @Test
    void valleyDetector_lineCount_isApproximatelyCorrect() {
        GridDetectorSettings settings = new GridDetectorSettings();
        settings.minLineH.set(30);
        settings.maxLineH.set(70);
        settings.minCharW.set(12);
        settings.maxCharW.set(40);

        GridDetectorProcessor proc = new GridDetectorProcessor(settings);
        GridDetectionResult result = proc.processFromSums(
                hRowSums, bgrImage.rows(), vColSums, bgrImage.cols());

        assertNotNull(result);
        // number of lines derivable from frame height / lineH
        double lineH = result.bestLineH();
        assumePositive(lineH, "bestLineH");
        int estimatedLines = (int) Math.round(bgrImage.rows() / lineH);
        assertEquals(EXPECTED_LINE_COUNT, estimatedLines, LINE_COUNT_TOLERANCE,
                String.format("Estimated %d lines (lineH=%.2f, frameH=%d)",
                        estimatedLines, lineH, bgrImage.rows()));

        proc.release();
    }

    @Test
    void valleyDetector_hValleys_countMatchesExpectedLines() {
        GridDetectorSettings settings = new GridDetectorSettings();
        settings.minLineH.set(30);
        settings.maxLineH.set(70);
        settings.minCharW.set(12);
        settings.maxCharW.set(40);

        GridDetectorProcessor proc = new GridDetectorProcessor(settings);
        GridDetectionResult result = proc.processFromSums(
                hRowSums, bgrImage.rows(), vColSums, bgrImage.cols());

        assertNotNull(result);
        // filtered valleys are the inter-line gaps; there should be LINES-1 = 16 or thereabouts
        int filteredValleys = result.hValleysFiltered().length;
        assertTrue(filteredValleys >= EXPECTED_LINE_COUNT - LINE_COUNT_TOLERANCE - 1
                        && filteredValleys <= EXPECTED_LINE_COUNT + LINE_COUNT_TOLERANCE,
                "Filtered H-valleys count: " + filteredValleys);

        proc.release();
    }

    // -------------------------------------------------------------------------
    // CorrelationGridDetectorProcessor
    // -------------------------------------------------------------------------

    @Test
    void correlationDetector_lineHeight_isInExpectedRange() {
        CorrelationGridDetectorSettings settings = new CorrelationGridDetectorSettings();
        settings.minLineHeight.set(30);
        settings.maxLineHeight.set(70);
        settings.smoothingAlpha.set(1.0); // no smoothing — single frame

        CorrelationGridDetectorProcessor proc = new CorrelationGridDetectorProcessor(settings);
        // the correlation detector works directly on the BGR mat
        CorrelationGridDetectionResult result = proc.process(bgrImage);

        assertNotNull(result);
        assertTrue(result.smoothedLineHeight() >= EXPECTED_LINE_H_MIN
                        && result.smoothedLineHeight() <= EXPECTED_LINE_H_MAX,
                String.format("smoothedLineHeight=%.2f not in [%.0f, %.0f]",
                        result.smoothedLineHeight(), EXPECTED_LINE_H_MIN, EXPECTED_LINE_H_MAX));
    }

    @Test
    void correlationDetector_lineCount_isApproximatelyCorrect() {
        CorrelationGridDetectorSettings settings = new CorrelationGridDetectorSettings();
        settings.minLineHeight.set(30);
        settings.maxLineHeight.set(70);
        settings.smoothingAlpha.set(1.0);

        CorrelationGridDetectorProcessor proc = new CorrelationGridDetectorProcessor(settings);
        CorrelationGridDetectionResult result = proc.process(bgrImage);

        assertNotNull(result);
        List<Integer> positions = result.linePositions();
        assertFalse(positions.isEmpty(), "linePositions must not be empty");
        assertEquals(EXPECTED_LINE_COUNT, positions.size(), LINE_COUNT_TOLERANCE,
                "linePositions.size()=" + positions.size());
    }

    @Test
    void correlationDetector_linePositions_areStrictlyIncreasing() {
        CorrelationGridDetectorSettings settings = new CorrelationGridDetectorSettings();
        settings.minLineHeight.set(30);
        settings.maxLineHeight.set(70);
        settings.smoothingAlpha.set(1.0);

        CorrelationGridDetectorProcessor proc = new CorrelationGridDetectorProcessor(settings);
        CorrelationGridDetectionResult result = proc.process(bgrImage);

        List<Integer> positions = result.linePositions();
        for (int i = 1; i < positions.size(); i++) {
            assertTrue(positions.get(i) > positions.get(i - 1),
                    "linePositions not strictly increasing at index " + i
                            + ": " + positions.get(i - 1) + " >= " + positions.get(i));
        }
    }

    @Test
    void correlationDetector_toGridDetectionResult_lineHIsPreserved() {
        CorrelationGridDetectorSettings settings = new CorrelationGridDetectorSettings();
        settings.minLineHeight.set(30);
        settings.maxLineHeight.set(70);
        settings.smoothingAlpha.set(1.0);

        CorrelationGridDetectorProcessor proc = new CorrelationGridDetectorProcessor(settings);
        CorrelationGridDetectionResult corrResult = proc.process(bgrImage);
        GridDetectionResult grid = corrResult.toGridDetectionResult();

        assertEquals(corrResult.smoothedLineHeight(), grid.bestLineH(), 1e-9,
                "toGridDetectionResult() must preserve smoothedLineHeight as bestLineH");
        assertEquals(corrResult.smoothedPhase(), grid.bestLineY0(), 1e-9,
                "toGridDetectionResult() must preserve smoothedPhase as bestLineY0");
    }

    // -------------------------------------------------------------------------
    // Static helper — avoids importing Assumptions
    // -------------------------------------------------------------------------

    private static void assumePositive(double value, String name) {
        org.junit.jupiter.api.Assumptions.assumeTrue(value > 0,
                name + " must be positive to continue this test");
    }
}
