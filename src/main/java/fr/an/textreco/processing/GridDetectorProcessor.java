package fr.an.textreco.processing;

import fr.an.textreco.model.GridDetectionResult;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Detects a regular character grid (fixed line-height and char-width) using a
 * Hough-period accumulator on each axis independently.
 *
 * For each lit pixel at position p and each candidate period T in [Tmin, Tmax]:
 *   acc[T - Tmin][p mod T] += pixelValue
 *
 * The peak cell (T*, o*) gives the best period and grid offset.
 * This is robust against missing strokes because it uses ALL pixels globally.
 *
 * Input: two pre-computed binary Mats (morphological opening + closing) that
 * together highlight both horizontal strokes (for Y) and vertical strokes (for X).
 */
public class GridDetectorProcessor {

    private final IntegerProperty minLineH = new SimpleIntegerProperty(8);
    private final IntegerProperty maxLineH = new SimpleIntegerProperty(60);
    private final IntegerProperty minCharW = new SimpleIntegerProperty(4);
    private final IntegerProperty maxCharW = new SimpleIntegerProperty(40);

    public IntegerProperty minLineHProperty() { return minLineH; }
    public int  getMinLineH()                 { return minLineH.get(); }
    public void setMinLineH(int v)            { minLineH.set(v); }

    public IntegerProperty maxLineHProperty() { return maxLineH; }
    public int  getMaxLineH()                 { return maxLineH.get(); }
    public void setMaxLineH(int v)            { maxLineH.set(v); }

    public IntegerProperty minCharWProperty() { return minCharW; }
    public int  getMinCharW()                 { return minCharW.get(); }
    public void setMinCharW(int v)            { minCharW.set(v); }

    public IntegerProperty maxCharWProperty() { return maxCharW; }
    public int  getMaxCharW()                 { return maxCharW.get(); }
    public void setMaxCharW(int v)            { maxCharW.set(v); }

    // scratch row/col sum buffers — reallocated only on dimension change
    private float[] rowSums = new float[0];
    private float[] colSums = new float[0];
    private final Mat reduceScratch = new Mat();

    public GridDetectionResult process(Mat morphHorizMat, Mat closeHorizMat,
                                       Mat morphVertMat,  Mat closeVertMat) {
        int w = morphHorizMat.cols();
        int h = morphHorizMat.rows();
        if (w == 0 || h == 0) return null;

        // --- build 1-D projection sums from the combined open+close signal ---
        // Y axis: sum over columns for each row → use horizontal morph (detects h-strokes)
        if (rowSums.length != h) rowSums = new float[h];
        addReduceRow(morphHorizMat, closeHorizMat, h, rowSums);

        // X axis: sum over rows for each col → use vertical morph (detects v-strokes)
        if (colSums.length != w) colSums = new float[w];
        addReduceCol(morphVertMat, closeVertMat, w, colSums);

        int minH = minLineH.get(), maxH = maxLineH.get();
        int minW = minCharW.get(), maxW = maxCharW.get();
        // clamp to valid ranges
        minH = Math.max(2, Math.min(minH, h / 2));
        maxH = Math.max(minH + 1, Math.min(maxH, h));
        minW = Math.max(2, Math.min(minW, w / 2));
        maxW = Math.max(minW + 1, Math.min(maxW, w));

        int numLineH = maxH - minH + 1;
        int numCharW = maxW - minW + 1;

        // --- Y accumulator ---
        float[][] accY = new float[numLineH][maxH]; // [periodIdx][offset], offset < period ≤ maxH
        for (int r = 0; r < h; r++) {
            float v = rowSums[r];
            if (v <= 0) continue;
            for (int T = minH; T <= maxH; T++) {
                int offset = r % T;
                accY[T - minH][offset] += v;
            }
        }

        // --- X accumulator ---
        float[][] accX = new float[numCharW][maxW];
        for (int c = 0; c < w; c++) {
            float v = colSums[c];
            if (v <= 0) continue;
            for (int T = minW; T <= maxW; T++) {
                int offset = c % T;
                accX[T - minW][offset] += v;
            }
        }

        // --- find Y peak ---
        int bestLineH = minH, bestLineY0 = 0;
        float bestYScore = -1;
        for (int Ti = 0; Ti < numLineH; Ti++) {
            int T = Ti + minH;
            // score = max offset vote / expected votes per offset bin
            // (bins near 0 get more votes due to text-area density, so use max)
            float maxVote = 0;
            int   maxOff  = 0;
            for (int o = 0; o < T; o++) {
                if (accY[Ti][o] > maxVote) { maxVote = accY[Ti][o]; maxOff = o; }
            }
            // normalise by the number of rows that fall into this offset bin for period T
            int binCount = h / T + (maxOff < h % T ? 1 : 0);
            float score = binCount > 0 ? maxVote / binCount : 0;
            if (score > bestYScore) { bestYScore = score; bestLineH = T; bestLineY0 = maxOff; }
        }

        // --- find X peak ---
        int bestCharW = minW, bestCharX0 = 0;
        float bestXScore = -1;
        for (int Ti = 0; Ti < numCharW; Ti++) {
            int T = Ti + minW;
            float maxVote = 0;
            int   maxOff  = 0;
            for (int o = 0; o < T; o++) {
                if (accX[Ti][o] > maxVote) { maxVote = accX[Ti][o]; maxOff = o; }
            }
            int binCount = w / T + (maxOff < w % T ? 1 : 0);
            float score = binCount > 0 ? maxVote / binCount : 0;
            if (score > bestXScore) { bestXScore = score; bestCharW = T; bestCharX0 = maxOff; }
        }

        return new GridDetectionResult(
                w, h,
                minH, maxH, bestLineH, bestLineY0, accY,
                minW, maxW, bestCharW, bestCharX0, accX);
    }

    /** Row-wise sum: for each row r, rowSums[r] = sum over columns of (matA[r,c] + matB[r,c]). */
    private void addReduceRow(Mat matA, Mat matB, int h, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        Mat rowMat = new Mat();
        org.opencv.core.Core.reduce(reduceScratch, rowMat, 1, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        rowMat.get(0, 0, out);
        rowMat.release();
    }

    /** Col-wise sum: for each col c, colSums[c] = sum over rows of (matA[r,c] + matB[r,c]). */
    private void addReduceCol(Mat matA, Mat matB, int w, float[] out) {
        org.opencv.core.Core.add(matA, matB, reduceScratch);
        Mat colMat = new Mat();
        org.opencv.core.Core.reduce(reduceScratch, colMat, 0, org.opencv.core.Core.REDUCE_SUM, CvType.CV_32F);
        colMat.get(0, 0, out);
        colMat.release();
    }

    public void release() {
        reduceScratch.release();
    }
}
