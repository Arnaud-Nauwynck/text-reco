package fr.an.textreco.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForcedValueModel {

    private final String label;
    private final String unit;

    private volatile double computedValue = 0.0;
    private volatile boolean force = false;
    private volatile double forcedValue = 0.0;

    public ForcedValueModel(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public ForcedValueModel(String label, String unit, double initialForced) {
        this(label, unit);
        this.forcedValue = initialForced;
    }
}
