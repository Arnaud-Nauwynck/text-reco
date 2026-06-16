package fr.an.textreco.ui.tab;

import fr.an.textreco.model.CorrelationGridDetectionResult;
import fr.an.textreco.ui.viewmodel.GridDetectViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Correlation tab focused on Y-axis period detection (text line height).
 */
public class LineHeightView extends AbstractCorrelationAxisView {

    public LineHeightView(GridDetectViewModel viewModel) {
        super(viewModel);
    }

    @Override
    protected AxisSpec describeAxis() {
        return new AxisSpec(
                "Line H", "lineH", "y0", "lines",
                Color.rgb(80, 160, 80, 0.7), Color.rgb(0, 220, 120, 0.8));
    }

    @Override
    protected double period(CorrelationGridDetectionResult r) {
        return r.smoothedLineHeight();
    }

    @Override
    protected double phase(CorrelationGridDetectionResult r) {
        return r.smoothedPhase();
    }

    @Override
    protected double[] projection(CorrelationGridDetectionResult r) {
        return r.projection();
    }

    @Override
    protected List<Integer> positions(CorrelationGridDetectionResult r) {
        return r.linePositions();
    }

    @Override
    protected IntegerProperty minRangeProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().minLineHeight;
    }

    @Override
    protected IntegerProperty maxRangeProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().maxLineHeight;
    }

    @Override
    protected BooleanProperty forceEnabledProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().forceLineHeight;
    }

    @Override
    protected DoubleProperty forcedValueProperty(GridDetectViewModel vm) {
        return vm.getCorrelationGridDetectorSettings().forcedLineHeight;
    }

}
