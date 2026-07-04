package fr.an.textreco.model;

import lombok.Data;

@Data
public class EdgeDetectorSettings {

    private volatile double cannyThreshold1 = 80;
    private volatile double cannyThreshold2 = 150;
}
