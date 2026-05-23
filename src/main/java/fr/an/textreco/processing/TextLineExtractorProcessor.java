package fr.an.textreco.processing;

import fr.an.textreco.model.AppSettings;
import fr.an.textreco.model.TextLine;
import fr.an.textreco.model.TextLineExtractionResult;
import fr.an.textreco.util.FxImageUtils;
import lombok.Getter;
import lombok.Setter;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects horizontal text lines in a perspective-corrected BGR frame.
 *
 * Algorithm:
 *  1. BGR → gray → adaptive threshold (binarise, inverted: text = white)
 *  2. Horizontal projection via Core.reduce(REDUCE_SUM) → float[h] row sums
 *  3. Normalise by frame width → fill ratio per row
 *  4. Threshold fill ratio to find "active" rows; merge close spans; filter by height
 *  5. Crop each span from the warped colour image into a WritableImage
 *
 * All Mats are allocated once as fields; rowSums float[] is reallocated only on height change.
 */
public class TextLineExtractorProcessor {

    @Getter @Setter private volatile double minFillRatio = 0.02;
    @Getter @Setter private volatile double maxFillRatio = 1.0;
    @Getter @Setter private volatile int    minLineGap    = 3;
    @Getter @Setter private volatile int    minLineHeight = 6;
    @Getter @Setter private volatile int    maxLineHeight = 120;

    // pre-allocated scratch Mats
    private final Mat gray      = new Mat();
    private final Mat binary    = new Mat();
    private final Mat rowSumMat = new Mat();   // 1-column, h rows, CV_32F after reduce

    // row-sum buffer — reallocated only when frame height changes
    private float[] rowSums = new float[0];

    private final AppSettings appSettings;

    // per-line crop ImageBuffers — grown lazily, never shrunk
    private final List<FxImageUtils.ImageBuffer> lineBuffers = new ArrayList<>();

    public TextLineExtractorProcessor(AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    public TextLineExtractionResult process(Mat warpedBgr) {
        int w = warpedBgr.cols();
        int h = warpedBgr.rows();
        if (w == 0 || h == 0) {
            return new TextLineExtractionResult(w, h, List.of(), new float[0]);
        }

        // --- binarise ---
        // dark theme: white text on black → invert so text pixels are 255 (lit)
        // light theme: black text on white → no invert needed
        int threshType = appSettings.isDarkTheme()
                ? Imgproc.THRESH_BINARY_INV
                : Imgproc.THRESH_BINARY;
        Imgproc.cvtColor(warpedBgr, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.adaptiveThreshold(gray, binary, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, threshType, 31, 10);

        // --- horizontal projection via OpenCV reduce ---
        // REDUCE_SUM along columns (dim=1) → result shape: (h, 1), type CV_32F
        Core.reduce(binary, rowSumMat, 1, Core.REDUCE_SUM, CvType.CV_32F);

        if (rowSums.length != h) {
            rowSums = new float[h];
        }
        rowSumMat.get(0, 0, rowSums);   // single bulk read, no per-row JNI call

        // --- threshold → active rows ---
        double lo = minFillRatio * w * 255.0;   // binary pixels are 255
        boolean[] active = new boolean[h];
        for (int r = 0; r < h; r++) {
            active[r] = rowSums[r] >= lo;
        }

        // --- collect raw spans (transitions in active[]) ---
        List<int[]> spans = new ArrayList<>();
        int spanStart = -1;
        for (int r = 0; r <= h; r++) {
            boolean on = r < h && active[r];
            if (on  && spanStart < 0) { spanStart = r; }
            if (!on && spanStart >= 0) { spans.add(new int[]{spanStart, r}); spanStart = -1; }
        }

        // --- merge spans whose gap < minLineGap ---
        int gap = minLineGap;
        List<int[]> merged = new ArrayList<>();
        for (int[] sp : spans) {
            if (!merged.isEmpty()) {
                int[] last = merged.get(merged.size() - 1);
                if (sp[0] - last[1] < gap) { last[1] = sp[1]; continue; }
            }
            merged.add(new int[]{sp[0], sp[1]});
        }

        // --- filter by height, crop, convert ---
        List<TextLine> lines = new ArrayList<>();
        int bufIdx = 0;
        int minH = minLineHeight, maxH = maxLineHeight;
        for (int[] sp : merged) {
            int lineH = sp[1] - sp[0];
            if (lineH < minH || lineH > maxH) continue;

            Mat crop = warpedBgr.submat(new Rect(0, sp[0], w, lineH));
            if (bufIdx >= lineBuffers.size()) lineBuffers.add(new FxImageUtils.ImageBuffer());
            lines.add(new TextLine(sp[0], sp[1], lineBuffers.get(bufIdx++).update(crop)));
        }

        return new TextLineExtractionResult(w, h, lines, rowSums.clone());
    }

    public void release() {
        gray.release();
        binary.release();
        rowSumMat.release();
        lineBuffers.forEach(FxImageUtils.ImageBuffer::release);
        lineBuffers.clear();
    }
}
