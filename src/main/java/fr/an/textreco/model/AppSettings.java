package fr.an.textreco.model;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class AppSettings implements ViewModel {

    /**
     * When true: terminal uses white text on black background (dark theme).
     * When false: black text on white background (light theme).
     * Affects adaptive-threshold polarity in text-line extraction.
     * <p>
     * JavaFX property — readable from background thread via get() which is volatile-safe.
     */
    private final BooleanProperty darkTheme = new SimpleBooleanProperty(true);

    public BooleanProperty darkThemeProperty() {
        return darkTheme;
    }

    public boolean isDarkTheme() {
        return darkTheme.get();
    }

    public void setDarkTheme(boolean v) {
        darkTheme.set(v);
    }
}
