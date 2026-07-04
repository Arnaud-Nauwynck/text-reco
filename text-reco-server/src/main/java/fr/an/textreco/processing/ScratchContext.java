package fr.an.textreco.processing;

import fr.an.textreco.util.MatFacade;
import org.opencv.core.Mat;

public class ScratchContext {

    public final Mat temp1 = MatFacade.alloc("ScratchContext.temp1");
    public final Mat temp2 = MatFacade.alloc("ScratchContext.temp2");
    public final Mat temp3 = MatFacade.alloc("ScratchContext.temp3");
    public final Mat rgbConvert = MatFacade.alloc("ScratchContext.rgbConvert");

    public void release() {
        MatFacade.release(temp1, "ScratchContext.temp1");
        MatFacade.release(temp2, "ScratchContext.temp2");
        MatFacade.release(temp3, "ScratchContext.temp3");
        MatFacade.release(rgbConvert, "ScratchContext.rgbConvert");
    }
}
