package fr.an.textreco.model;

public class GridDetectCoordModel {

    public final ForcedValueModel lineHeight = new ForcedValueModel("LineH:", "px", 28.0);
    public final ForcedValueModel charWidth = new ForcedValueModel("CharW:", "px", 14.0);
    public final ForcedValueModel lineOffset = new ForcedValueModel("y0", "px", 0.0);
    public final ForcedValueModel charOffset = new ForcedValueModel("x0", "px", 0.0);
}
