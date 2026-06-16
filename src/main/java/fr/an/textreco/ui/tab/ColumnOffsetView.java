package fr.an.textreco.ui.tab;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Correlation tab focused on X-axis gap phase (column offset / x0).
 */
public class ColumnOffsetView extends AbstractCorrelationAxisView {

    public ColumnOffsetView(GridDetectViewModel viewModel) {
        super(viewModel);
    }

    @Override
    protected AxisSpec describeAxis() {
        return new AxisSpec(
                "x0", "charW", "x0", "cols",
                Color.rgb(120, 100, 160, 0.7), Color.rgb(255, 200, 0, 0.85));
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
        return vm.getCorrelationGridDetectorSettings().forceColOffset;
    }

    @Override
    protected DoubleProperty forcedValueProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().forcedColOffset;
    }

}
