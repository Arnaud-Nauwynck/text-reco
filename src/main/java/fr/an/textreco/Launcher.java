package fr.an.textreco;

import fr.an.textreco.ui.TextRecoJavaFxApplication;

public class Launcher {

    public static void main(String[] args) {
        System.out.println("starting ...");
        try {
            TextRecoJavaFxApplication.launch(TextRecoJavaFxApplication.class, args);
        } catch (Exception e) {
            System.out.println("Error exiting application: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
