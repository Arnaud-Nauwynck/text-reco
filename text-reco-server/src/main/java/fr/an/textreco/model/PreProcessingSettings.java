package fr.an.textreco.model;

import lombok.Data;

@Data
public class PreProcessingSettings {

    private volatile BinarizationMethod binarizationMethod = BinarizationMethod.TOPHAT;
    private volatile int tophatRadius = 12;
    private volatile int tophatThreshold = 20;
    private volatile int adaptiveBlock = 31;
    private volatile int adaptiveC = 10;
    private volatile int seHalfLen = 7;
}
