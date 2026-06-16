package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Reusable model for a single "forced numeric value" control group.
 *
 * <p>Captures the {@code label : computed [x] Force [value] unit} pattern
 * that recurs across the grid-detector tabs (Line Offset, Char Offset, Line
 * Height, Char Width, …). Holds the static descriptors (label, unit) and the
 * three observable pieces a view binds to:
 * <ul>
 *   <li>{@link #computedValue} — the auto-detected value (the single source of
 *       truth; the view formats it for display);</li>
 *   <li>{@link #force} — whether the forced value overrides the computed one;</li>
 *   <li>{@link #forcedValue} — the user-entered override, editable only while
 *       {@link #force} is set.</li>
 * </ul>
 *
 * <p>All properties are JavaFX-observable so the matching {@code ForcedValueView}
 * binds directly and the processing layer can read {@code force.get()} /
 * {@code forcedValue.get()} on every frame.
 */
public class ForcedValueModel implements ViewModel {

    /** Static row label, e.g. {@code "Line offset:"}. */
    private final String label;

    /** Static unit suffix shown after the spinner, e.g. {@code "px"}. */
    private final String unit;

    /** Auto-detected value; seeds {@link #forcedValue} when Force is switched on. */
    public final DoubleProperty computedValue = new SimpleDoubleProperty(0.0);

    /** When true, {@link #forcedValue} overrides the computed value. */
    public final BooleanProperty force = new SimpleBooleanProperty(false);

    /** User-entered override; editable only while {@link #force} is set. */
    public final DoubleProperty forcedValue = new SimpleDoubleProperty(0.0);

    public ForcedValueModel(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public ForcedValueModel(String label, String unit, double initialForced) {
        this(label, unit);
        this.forcedValue.set(initialForced);
    }

    public String getLabel() {
        return label;
    }

    public String getUnit() {
        return unit;
    }
}
