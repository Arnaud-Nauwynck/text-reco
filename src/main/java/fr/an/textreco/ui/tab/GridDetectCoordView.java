package fr.an.textreco.ui.tab;

import fr.an.textreco.model.ForcedValueModel;
import fr.an.textreco.model.GridDetectCoordModel;
import fr.an.textreco.model.GridDetectionResult;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Reusable view bundling the four grid-coordinate "forced value" controls
 * (line height / char width / line offset / char offset) with their shared
 * {@link GridDetectCoordModel}.
 *
 * <p>Owns the four reusable {@link ForcedValueView}s and the small operations
 * a tab needs over them: laying them out as two rows (period / offset), wiring
 * a refresh callback, publishing the detector's computed values, and turning a
 * forced offset into a crop delta. The same model instance is bound across the
 * Char Classifier / Grid Detect / correlation-axis tabs, so a force or value
 * change in one view is reflected in all of them.
 *
 * <p>As a {@link VBox} the view can be dropped straight into a tab (it stacks
 * the period row above the offset row); callers that want the rows separately
 * can use {@link #periodRow()} / {@link #offsetRow()}.
 */
public class GridDetectCoordView extends VBox {

    private final GridDetectCoordModel model;
    private final ForcedValueView lineHeightView;
    private final ForcedValueView charWidthView;
    private final ForcedValueView lineOffsetView;
    private final ForcedValueView charOffsetView;

    private final HBox periodRow;
    private final HBox offsetRow;

    public GridDetectCoordView(GridDetectCoordModel model) {
        super(4);
        this.model = model;
        this.lineHeightView = new ForcedValueView(model.lineHeight, 1.0, 200.0, 0.1);
        this.charWidthView = new ForcedValueView(model.charWidth, 0.1, 200.0, 0.1);
        this.lineOffsetView = new ForcedValueView(model.lineOffset, -500.0, 500.0, 1.0);
        this.charOffsetView = new ForcedValueView(model.charOffset, -500.0, 500.0, 1.0);

        this.periodRow = hrow(lineHeightView, styledLabel("   "), charWidthView);
        this.offsetRow = hrow(lineOffsetView, styledLabel("   "), charOffsetView);

        setAlignment(Pos.TOP_LEFT);
        getChildren().addAll(periodRow, offsetRow);
    }

    /** Period controls (line height + char width) as one row. */
    public HBox periodRow() {
        return periodRow;
    }

    /** Offset controls (line offset + char offset) as one row. */
    public HBox offsetRow() {
        return offsetRow;
    }

    /**
     * Runs {@code onChange} whenever any force toggle or forced value changes
     * across the four models. The {@link ForcedValueView}s already enable their
     * spinner only while "Force" is ticked.
     */
    public void addRefreshListener(Runnable onChange) {
        for (ForcedValueModel fv : new ForcedValueModel[]{
                model.lineHeight, model.charWidth, model.lineOffset, model.charOffset}) {
            fv.force.addListener((obs, o, n) -> onChange.run());
            fv.forcedValue.addListener((obs, o, n) -> onChange.run());
        }
    }

    /**
     * Publishes the detector's computed (optimal) grid values so each view shows
     * the auto value next to its override.
     */
    public void updateComputed(GridDetectionResult grid) {
        if (grid == null) return;
        model.lineHeight.computedValue.set(grid.bestLineH());
        model.charWidth.computedValue.set(grid.bestCharW());
        model.lineOffset.computedValue.set(grid.bestLineY0());
        model.charOffset.computedValue.set(grid.bestCharX0());
    }

    /**
     * Horizontal crop delta (px) added to the grid's column starts. Zero when
     * not forced (use the computed {@code bestCharX0}); otherwise the forced x0
     * minus the computed one, so {@code colStarts + delta} lands on the forced
     * grid.
     */
    public int effectiveColOffset(GridDetectionResult grid) {
        if (!model.charOffset.force.get() || grid == null) return 0;
        return (int) Math.round(model.charOffset.forcedValue.get())
                - (int) Math.round(grid.bestCharX0());
    }

    /**
     * Vertical crop delta (px) shifting the column crop window within the line
     * image. Zero when not forced (use the computed {@code bestLineY0});
     * otherwise the forced y0 minus the computed one.
     */
    public int effectiveLineOffset(GridDetectionResult grid) {
        if (!model.lineOffset.force.get() || grid == null) return 0;
        return (int) Math.round(model.lineOffset.forcedValue.get())
                - (int) Math.round(grid.bestLineY0());
    }

    // -------------------------------------------------------------------------
    // small UI helpers (mirror the styling used across the tab views)
    // -------------------------------------------------------------------------

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static HBox hrow(javafx.scene.Node... nodes) {
        HBox b = new HBox(8, nodes);
        b.setAlignment(Pos.CENTER_LEFT);
        return b;
    }
}
