package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.model.PreProcessingResult;
import fr.an.textreco.ui.viewmodel.PreProcessingViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class PreProcessingView extends VBox implements JavaView<PreProcessingViewModel> {

    private static final double BIG_W   = 320;
    private static final double BIG_H   = 240;
    private static final double MORPH_W = 200;
    private static final double MORPH_H = 150;

    @Getter
    private final VBox root = new VBox(8);

    private final ImageView binaryView    = imageView(BIG_W,   BIG_H);
    private final ImageView morphHView    = imageView(MORPH_W, MORPH_H);
    private final ImageView morphVView    = imageView(MORPH_W, MORPH_H);
    private final ImageView morphFwdView  = imageView(MORPH_W, MORPH_H);
    private final ImageView morphBwdView  = imageView(MORPH_W, MORPH_H);
    private final ImageView closeHView    = imageView(MORPH_W, MORPH_H);
    private final ImageView closeVView    = imageView(MORPH_W, MORPH_H);
    private final ImageView closeFwdView  = imageView(MORPH_W, MORPH_H);
    private final ImageView closeBwdView  = imageView(MORPH_W, MORPH_H);

    public PreProcessingView(PreProcessingViewModel viewModel) {
        viewModel.preProcessingProperty().addListener((obs, o, r) -> onResult(r));
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #1e1e1e;");

        HBox topRow = new HBox(8, panel("Binary", binaryView));
        topRow.setAlignment(Pos.TOP_LEFT);

        HBox morphRow = new HBox(8,
                panel("Open  —",  morphHView),
                panel("Open  |",  morphVView),
                panel("Open  /",  morphFwdView),
                panel("Open  \\", morphBwdView));
        morphRow.setAlignment(Pos.TOP_LEFT);

        HBox closeRow = new HBox(8,
                panel("Close  —",  closeHView),
                panel("Close  |",  closeVView),
                panel("Close  /",  closeFwdView),
                panel("Close  \\", closeBwdView));
        closeRow.setAlignment(Pos.TOP_LEFT);

        root.getChildren().addAll(
                topRow,
                sectionLabel("Morphological Openings"), morphRow,
                sectionLabel("Morphological Closings"), closeRow);
    }

    private void onResult(PreProcessingResult r) {
        if (r == null) return;
        binaryView  .setImage(r.binaryImage());
        morphHView  .setImage(r.morphHoriz());
        morphVView  .setImage(r.morphVert());
        morphFwdView.setImage(r.morphDiagFwd());
        morphBwdView.setImage(r.morphDiagBwd());
        closeHView  .setImage(r.closeHoriz());
        closeVView  .setImage(r.closeVert());
        closeFwdView.setImage(r.closeDiagFwd());
        closeBwdView.setImage(r.closeDiagBwd());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static ImageView imageView(double w, double h) {
        ImageView iv = new ImageView();
        iv.setPreserveRatio(true);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setSmooth(false);
        return iv;
    }

    private VBox panel(String title, javafx.scene.Node content) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #bbbbbb;");
        lbl.setFont(Font.font("System", FontWeight.BOLD, 11));
        BorderPane frame = new BorderPane(content);
        frame.setStyle("-fx-background-color: #2a2a2a; -fx-border-color: #505050; -fx-border-width: 1;");
        frame.setPadding(new Insets(3));
        VBox p = new VBox(3, lbl, frame);
        p.setAlignment(Pos.TOP_LEFT);
        return p;
    }

    private Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 13));
        l.setStyle("-fx-text-fill: #aaaaff;");
        return l;
    }
}
