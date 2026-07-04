package fr.an.textreco.util;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single entry point (Facade) for every OpenCV {@link Mat} allocation in the app.
 */
public final class MatFacade {

    private MatFacade() {
    }

    public enum Mode {
        INIT,
        WARMUP,
        RUN_LOOP
    }

    private static volatile Mode mode = Mode.INIT;
    private static final int WARMUP_FRAMES = 3;
    private static volatile int warmupFramesLeft = 0;
    private static final AtomicLong runLoopAllocs = new AtomicLong();
    private static final AtomicLong warmupAllocs = new AtomicLong();
    private static volatile boolean warnOnRunLoopAlloc = true;
    private static final ThreadLocal<Integer> expectedDepth = ThreadLocal.withInitial(() -> 0);
    private static final AtomicLong expectedAllocs = new AtomicLong();

    public static void beginInit() {
        mode = Mode.INIT;
    }

    public static void beginRunLoop() {
        warmupFramesLeft = WARMUP_FRAMES;
        mode = WARMUP_FRAMES > 0 ? Mode.WARMUP : Mode.RUN_LOOP;
    }

    public static void frameTick() {
        if (mode != Mode.WARMUP) {
            return;
        }
        if (--warmupFramesLeft <= 0) {
            mode = Mode.RUN_LOOP;
        }
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isInitMode() {
        return mode == Mode.INIT;
    }

    public static long runLoopAllocCount() {
        return runLoopAllocs.get();
    }

    public static long warmupAllocCount() {
        return warmupAllocs.get();
    }

    public static void setWarnOnRunLoopAlloc(boolean warn) {
        warnOnRunLoopAlloc = warn;
    }

    public static void expectAllocations(Runnable body) {
        expectedDepth.set(expectedDepth.get() + 1);
        try {
            body.run();
        } finally {
            expectedDepth.set(expectedDepth.get() - 1);
        }
    }

    public static long expectedAllocCount() {
        return expectedAllocs.get();
    }

    public static Mat alloc(String tag) {
        return track(new Mat(), tag);
    }

    public static Mat alloc(int rows, int cols, int type, String tag) {
        return track(new Mat(rows, cols, type), tag);
    }

    public static Mat alloc(Size size, int type, String tag) {
        return track(new Mat(size, type), tag);
    }

    public static Mat allocOnes(int rows, int cols, int type, String tag) {
        return track(Mat.ones(rows, cols, type), tag);
    }

    public static Mat allocZeros(int rows, int cols, int type, String tag) {
        return track(Mat.zeros(rows, cols, type), tag);
    }

    public static MatOfByte allocMatOfByte(String tag) {
        MatOfByte m = new MatOfByte();
        track(m, tag);
        return m;
    }

    public static MatOfPoint2f allocMatOfPoint2f(String tag) {
        MatOfPoint2f m = new MatOfPoint2f();
        track(m, tag);
        return m;
    }

    public static <M extends Mat> M adopt(M mat, String tag) {
        return track(mat, tag);
    }

    public static void release(Mat mat, String tag) {
        if (mat == null) {
            return;
        }
        MatTracker.free(mat, tag);
        mat.release();
    }

    private static <M extends Mat> M track(M mat, String tag) {
        MatTracker.alloc(mat, tag);
        if (expectedDepth.get() > 0) {
            expectedAllocs.incrementAndGet();
            return mat;
        }
        switch (mode) {
            case WARMUP -> warmupAllocs.incrementAndGet();
            case RUN_LOOP -> {
                long n = runLoopAllocs.incrementAndGet();
                if (warnOnRunLoopAlloc) {
                    Exception trace = new Exception("Mat allocated during RUN_LOOP: " + tag);
                    System.err.printf(
                            "[MatFacade] WARNING abnormal runLoop allocation #%d (tag=%s)%n",
                            n, tag);
                    trace.printStackTrace();
                }
            }
            case INIT -> { /* expected */ }
        }
        return mat;
    }
}
