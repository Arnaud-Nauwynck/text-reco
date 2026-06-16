package fr.an.textreco.ui.tab;

import fr.an.textreco.model.ForcedValueModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import lombok.Getter;

import java.util.Locale;

/**
 * Reusable view component for a single "forced numeric value" control group.
 * It stays compact by showing the spinner only while forced; the computed value
 * is always visible:
 *
 * <pre>
 *   force off:  Line offset:  12.30  [ ]  px
 *   force on:   Line offset:  12.30  [x]  [ 14.00 ]  px
 * </pre>
 *
 * <p>Binds to a {@link ForcedValueModel}: the spinner is bound to
 * {@code model.forcedValue} and is visible (and editable) only while forced; the
 * (label-less) checkbox is bidirectionally bound to {@code model.force}. The
 * read-only computed label formats {@code model.computedValue} and is always
 * shown, so the auto-detected value stays visible next to any override. Enabling
 * Force seeds {@code forcedValue} with the current computed value, so editing
 * starts where the displayed value left off. The spinner is un-managed while
 * hidden, so the row collapses to its minimum width. This factors out the
 * offset/period control rows duplicated across {@code CharClassifierView},
 * {@code GridDetectView} and the correlation-axis tabs.
 */
public class ForcedValueView extends HBox {

    @Getter
    private final ForcedValueModel model;

    private final Label computedLabel = monoLabel("—");
    private final CheckBox forceCb;
    private final Spinner<Double> forcedSp;

    public ForcedValueView(ForcedValueModel model) {
        this(model, 0.0, 500.0, 0.1);
    }

    public ForcedValueView(ForcedValueModel model, double min, double max, double step) {
        super(8);
        this.model = model;
        this.forceCb = styledCheckBox("");
        this.forcedSp = dblSpinner(min, max, step);

        setAlignment(Pos.CENTER_LEFT);

        // computed value (read-only), formatted from the numeric source of truth.
        // Always shown so the auto-detected value stays visible next to any override.
        computedLabel.textProperty().bind(
                Bindings.createStringBinding(
                        () -> String.format(Locale.ROOT, "%.2f", model.computedValue.get()),
                        model.computedValue));

        // force checkbox <-> model.force. Enabling Force starts editing from the
        // current computed value (don't reuse the previous/initial forced value).
        forceCb.selectedProperty().bindBidirectional(model.force);
        model.force.addListener((obs, was, now) -> {
            if (now && !was) model.forcedValue.set(model.computedValue.get());
        });

        // spinner <-> model.forcedValue; shown (and editable) only while forced.
        // visible/managed both track force so the hidden control takes no space.
        bindDblSpinner(forcedSp, model.forcedValue);
        forcedSp.visibleProperty().bind(model.force);
        forcedSp.managedProperty().bind(forcedSp.visibleProperty());

        getChildren().addAll(
                styledLabel(model.getLabel()), computedLabel,
                forceCb, forcedSp, styledLabel(model.getUnit()));
    }

    // -------------------------------------------------------------------------
    // helpers (mirror the styling used across the tab views)
    // -------------------------------------------------------------------------

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private static Label monoLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        return l;
    }

    private static CheckBox styledCheckBox(String text) {
        CheckBox cb = new CheckBox(text);
        cb.setStyle("-fx-text-fill: #cccccc;");
        return cb;
    }

    private static Spinner<Double> dblSpinner(double min, double max, double step) {
        Spinner<Double> s = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, min, step));
        s.setEditable(true);
        s.setPrefWidth(90);
        s.setStyle("-fx-background-color: #3a3a3a;");
        s.getEditor().setStyle("-fx-background-color: #3a3a3a; -fx-text-fill: #eeeeee; -fx-font-family: monospace;");
        ((SpinnerValueFactory.DoubleSpinnerValueFactory) s.getValueFactory())
                .setConverter(new javafx.util.StringConverter<Double>() {
                    @Override
                    public String toString(Double v) {
                        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
                    }

                    @Override
                    public Double fromString(String str) {
                        if (str == null || str.isBlank()) return 0.0;
                        return Double.parseDouble(str.trim().replace(',', '.'));
                    }
                });
        return s;
    }

    private static void bindDblSpinner(Spinner<Double> s, DoubleProperty prop) {
        s.getValueFactory().setValue(prop.get());
        s.valueProperty().addListener((obs, o, n) -> {
            if (n != null) prop.set(n);
        });
        prop.addListener((obs, o, n) -> {
            if (Math.abs(n.doubleValue() - s.getValue()) > 0.001) s.getValueFactory().setValue(n.doubleValue());
        });
    }
}
