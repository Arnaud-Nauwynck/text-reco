package fr.an.textreco.ui.tab;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Correlation tab focused on X-axis period detection (character width).
 */
public class CharWidthView extends AbstractCorrelationAxisView {

    public CharWidthView(GridDetectViewModel viewModel) {
        super(viewModel);
    }

    @Override
    protected AxisSpec describeAxis() {
        return new AxisSpec(
                "Char Width", "charW", "x0", "cols",
                Color.rgb(160, 140, 70, 0.7), Color.rgb(255, 200, 0, 0.8));
    }

    @Override
    protected double period(CorrelationGridDetectionResult r) {
        return r.smoothedCharWidth();
    }

    @Override
    protected double phase(CorrelationGridDetectionResult r) {
        return r.smoothedColPhase();
    }

    @Override
    protected double[] projection(CorrelationGridDetectionResult r) {
        return r.colProjection();
    }

    @Override
    protected List<Integer> positions(CorrelationGridDetectionResult r) {
        return r.columnPositions();
    }

    @Override
    protected IntegerProperty minRangeProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().minCharWidth;
    }

    @Override
    protected IntegerProperty maxRangeProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().maxCharWidth;
    }

    @Override
    protected BooleanProperty forceEnabledProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().forceCharWidth;
    }

    @Override
    protected DoubleProperty forcedValueProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().forcedCharWidth;
    }

}
