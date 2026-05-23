package fr.an.textreco.processing;

import fr.an.textreco.model.FrameData;

public interface FrameProcessor {

    void process(
            FrameData frame,
            ProcessingContext context
    );
}