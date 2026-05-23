package fr.an.textreco.model;

import lombok.Getter;
import lombok.Setter;

public class AppSettings {

    /**
     * When true: terminal uses white text on black background (dark theme).
     * When false: black text on white background (light theme).
     * Affects adaptive-threshold polarity in text-line extraction.
     */
    @Getter @Setter private volatile boolean darkTheme = true;
}
