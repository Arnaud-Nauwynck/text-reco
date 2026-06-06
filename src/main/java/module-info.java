module textreco {
    requires javafx.controls;
    requires javafx.graphics;
    requires opencv;
    requires static lombok;
    requires java.desktop;
    requires tess4j;
    requires de.saxsys.mvvmfx;

    exports fr.an.textreco;
    exports fr.an.textreco.model;
    exports fr.an.textreco.processing;
    exports fr.an.textreco.ui;
    exports fr.an.textreco.ui.tab;
    exports fr.an.textreco.ui.viewmodel;
}