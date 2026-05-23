package fr.an.textreco.model;

public enum BinarizationMethod {
    ADAPTIVE,       // adaptiveThreshold (original)
    TOPHAT,         // morphological top-hat + fixed threshold
    OTSU            // Otsu global threshold
}
