package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class EdgeDetectorSettings implements ViewModel {

    private final DoubleProperty cannyThreshold1 = new SimpleDoubleProperty(80);
    private final DoubleProperty cannyThreshold2 = new SimpleDoubleProperty(150);

    public DoubleProperty cannyThreshold1Property() { return cannyThreshold1; }
    public double getCannyThreshold1()              { return cannyThreshold1.get(); }
    public void   setCannyThreshold1(double v)      { cannyThreshold1.set(v); }

    public DoubleProperty cannyThreshold2Property() { return cannyThreshold2; }
    public double getCannyThreshold2()              { return cannyThreshold2.get(); }
    public void   setCannyThreshold2(double v)      { cannyThreshold2.set(v); }
}
