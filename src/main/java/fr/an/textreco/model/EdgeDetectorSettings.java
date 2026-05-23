package fr.an.textreco.model;

import lombok.Getter;
import lombok.Setter;

public class EdgeDetectorSettings {

    @Getter @Setter private volatile double cannyThreshold1 = 80;
    @Getter @Setter private volatile double cannyThreshold2 = 150;
}
