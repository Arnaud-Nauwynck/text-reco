package fr.an.textreco.processing;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.model.CorrelationGridDetectorSettings;
import fr.an.textreco.util.MatFacade;
import lombok.Getter;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust terminal grid detector orchestrating four single-responsibility
 * detectors over the autocorrelation pipeline:
 * <ol>
 *   <li>{@link LineHeightDetector} — Y-axis period (line height)</li>
 *   <li>{@link CharWidthDetector} — X-axis period (char width)</li>
 *   <li>{@link LineOffsetDetector} — Y-axis gap phase (line offset)</li>
 *   <li>{@link ColumnOffsetDetector} — X-axis gap phase (column offset)</li>
 * </ol>
 *
 * <p>This class only builds the row/column projection histograms, feeds them to
 * the detectors, and assembles the {@link CorrelationGridDetectionResult}.  The
 * signal math lives in {@link CorrelationMath}; cross-frame smoothing lives in
 * each detector.
 */
public class CorrelationGridDetectorProcessor {

    @Getter
    private final CorrelationGridDetectorSettings settings;

    private final LineHeightDetector lineHeightDetector = new LineHeightDetector();
    private final CharWidthDetector charWidthDetector = new CharWidthDetector();
    private final LineOffsetDetector lineOffsetDetector = new LineOffsetDetector();
    private final ColumnOffsetDetector columnOffsetDetector = new ColumnOffsetDetector();

    // pre-allocated scratch for buildProjection — reused every frame
    private final Mat gray = MatFacade.alloc("CorrelationGridDetector.gray");
    private final Mat kernel =
            MatFacade.adopt(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 1)),
                    "CorrelationGridDetector.kernel");

    public CorrelationGridDetectorProcessor(CorrelationGridDetectorSettings settings) {
        this.settings = settings;
    }

    public void release() {
        MatFacade.release(gray, "CorrelationGridDetector.gray");
        MatFacade.release(kernel, "CorrelationGridDetector.kernel");
    }

    public CorrelationGridDetectionResult process(Mat frame) {
        int minH = settings.minLineHeight.get();
        int maxH = settings.maxLineHeight.get();
        int minW = settings.minCharWidth.get();
        int maxW = settings.maxCharWidth.get();
        double alpha = settings.smoothingAlpha.get();

        prepareGray(frame);
        double[] rowProj = buildRowProjection();
        double[] colProj = buildColProjection();

        // ---- Y axis: line height, then line offset ----
        double lineHeight = settings.forceLineHeight.get()
                ? Math.max(0.1, settings.forcedLineHeight.get())
                : lineHeightDetector.detect(rowProj, minH, maxH, alpha);
        double lineOffset;
        if (settings.forceLineOffset.get()) {
            lineOffset = settings.forcedLineOffset.get();
        } else if (lineHeight > 0) {
            lineOffset = lineOffsetDetector.detect(rowProj, lineHeight, alpha);
        } else {
            lineOffset = Math.max(0, lineOffsetDetector.getSmoothedPhase());
        }

        // ---- X axis: char width, then column offset ----
        double charWidth = settings.forceCharWidth.get()
                ? Math.max(0.1, settings.forcedCharWidth.get())
                : charWidthDetector.detect(colProj, minW, maxW, alpha);
        double colOffset;
        if (settings.forceColOffset.get()) {
            colOffset = settings.forcedColOffset.get();
        } else if (charWidth > 0) {
            colOffset = columnOffsetDetector.detect(colProj, charWidth, alpha);
        } else {
            colOffset = Math.max(0, columnOffsetDetector.getSmoothedPhase());
        }

        List<Integer> linePositions = lineHeight > 0
                ? gridCellCentres(lineOffset, lineHeight, frame.rows())
                : new ArrayList<>();
        List<Integer> columnPositions = charWidth > 0
                ? gridCellCentres(colOffset, charWidth, frame.cols())
                : new ArrayList<>();

        return new CorrelationGridDetectionResult(
                frame.cols(), frame.rows(),
                Math.max(0, lineHeight), lineOffset, rowProj, linePositions,
                Math.max(0, charWidth), colOffset, colProj, columnPositions);
    }

    /**
     * Reset smoothing state of all detectors (e.g. on source switch).
     */
    public void reset() {
        lineHeightDetector.reset();
        charWidthDetector.reset();
        lineOffsetDetector.reset();
        columnOffsetDetector.reset();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Projection building
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Converts {@code frame} to an inverted, horizontally-eroded greyscale in
     * {@link #gray} so dark text becomes bright signal for projection.
     */
    private void prepareGray(Mat frame) {
        if (frame.channels() == 3)
            Imgproc.cvtColor(frame, gray, Imgproc.COLOR_BGR2GRAY);
        else
            frame.copyTo(gray);

        Core.bitwise_not(gray, gray);
        Imgproc.erode(gray, gray, kernel);

        int rows = gray.rows();
        int cols = gray.cols();
        if (pixBuf.length != rows * cols) pixBuf = new byte[rows * cols];
        gray.get(0, 0, pixBuf);
    }

    /**
     * Horizontal projection: sum of each row over the usable columns.
     */
    private double[] buildRowProjection() {
        int rows = gray.rows();
        int cols = gray.cols();
        int usableCols = Math.max(cols - 15, cols / 2);
        double[] proj = new double[rows];
        for (int y = 0; y < rows; y++) {
            int base = y * cols;
            double sum = 0;
            for (int x = 0; x < usableCols; x++)
                sum += pixBuf[base + x] & 0xFF;
            proj[y] = sum;
        }
        return proj;
    }

    /**
     * Vertical projection: sum of each column over all rows.
     */
    private double[] buildColProjection() {
        int rows = gray.rows();
        int cols = gray.cols();
        double[] proj = new double[cols];
        for (int y = 0; y < rows; y++) {
            int base = y * cols;
            for (int x = 0; x < cols; x++)
                proj[x] += pixBuf[base + x] & 0xFF;
        }
        return proj;
    }

    // reused pixel buffer for the projections — grown only on dimension change
    private byte[] pixBuf = new byte[0];

    // ────────────────────────────────────────────────────────────────────────
    // Output positions
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Cell centres along one axis: the gap {@code phase} marks inter-cell gaps,
     * so cell centres (line tops / char centres) sit half a period {@code T}
     * away.  Used for both the Y axis (line positions) and the X axis (column
     * positions).
     */
    private static List<Integer> gridCellCentres(double phase, double T, int extent) {
        List<Integer> positions = new ArrayList<>();
        double first = (phase + T * 0.5) % T;
        if (first < 0) first += T;
        for (double p = first; p < extent; p += T)
            positions.add((int) Math.round(p));
        return positions;
    }
}
