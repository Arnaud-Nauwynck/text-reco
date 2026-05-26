package fr.an.textreco.ui.tab;

import fr.an.textreco.processing.CharTemplateDb;
import fr.an.textreco.processing.PreComputedFeaturesChar;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import lombok.Getter;

import java.util.Map;

/**
 * Tab that displays the pre-computed features for every character template.
 * Each card is built by {@link CharFeatureCardBuilder}.
 */
public class CharFeaturesView {

    private static final int CARD_SPACING = 6;

    @Getter
    private final ScrollPane root;

    public CharFeaturesView(CharTemplateDb db) {
        FlowPane flow = new FlowPane(CARD_SPACING, CARD_SPACING);
        flow.setPadding(new Insets(10));
        flow.setStyle("-fx-background-color: #1e1e1e;");

        Map<Character, PreComputedFeaturesChar> features = db.getCharFeatures();
        for (Map.Entry<Character, PreComputedFeaturesChar> e : features.entrySet()) {
            flow.getChildren().add(CharFeatureCardBuilder.buildCard(e.getKey(), e.getValue()));
        }

        root = new ScrollPane(flow);
        root.setFitToWidth(true);
        root.setStyle("-fx-background-color: #1e1e1e; -fx-background: #1e1e1e;");
    }
}
