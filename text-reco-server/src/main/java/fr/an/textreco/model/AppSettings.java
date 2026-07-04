package fr.an.textreco.model;

import lombok.Data;

@Data
public class AppSettings {

    /**
     * When true: terminal uses white text on black background (dark theme).
     * When false: black text on white background (light theme).
     */
    private volatile boolean darkTheme = true;
}
