package fr.an.textreco.model;

import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;

public class FrameData {

    public final Mat raw = MatFacade.alloc("FrameData.raw");

    public final Mat gray = MatFacade.alloc("FrameData.gray");

    public final Mat processed = MatFacade.alloc("FrameData.processed");

    public FrameData() {
    }

    public void release() {
        MatFacade.release(raw, "FrameData.raw");
        MatFacade.release(gray, "FrameData.gray");
        MatFacade.release(processed, "FrameData.processed");
    }
}