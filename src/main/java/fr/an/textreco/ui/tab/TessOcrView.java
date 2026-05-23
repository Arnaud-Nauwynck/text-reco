package fr.an.textreco.ui.tab;

import fr.an.textreco.ui.ProcessingPipeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class TessOcrView {

    @Getter
    private final BorderPane root = new BorderPane();

    private final TextArea textArea   = new TextArea();
    private final Label statusLabel   = new Label("OCR disabled.");

    public TessOcrView(ProcessingPipeline pipeline) {
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setFont(Font.font("Monospaced", 13));
        textArea.setStyle("-fx-control-inner-background: #1a1a1a; -fx-text-fill: #e8e8e8; -fx-background-color: #1a1a1a;");

        statusLabel.setStyle("-fx-text-fill: #888888;");
        statusLabel.setFont(Font.font("System", FontWeight.NORMAL, 11));

        CheckBox enableBox = new CheckBox("Enable OCR every frame");
        enableBox.setStyle("-fx-text-fill: #cccccc;");
        enableBox.selectedProperty().bindBidirectional(pipeline.getOcrEnabledProperty());

        Button runOnceBtn = new Button("Run once");
        runOnceBtn.setStyle("-fx-background-color: #3a5a3a; -fx-text-fill: #dddddd;");
        runOnceBtn.setOnAction(e -> pipeline.requestOcrOnce());

        HBox toolbar = new HBox(12, enableBox, runOnceBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 8, 4, 8));

        VBox top = new VBox(4, toolbar, statusLabel);
        top.setPadding(new Insets(0, 8, 4, 8));

        root.setTop(top);
        root.setCenter(textArea);
        root.setStyle("-fx-background-color: #1e1e1e;");

        pipeline.getTessOcrProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                textArea.setText(newVal);
            } else {
                textArea.clear();
                statusLabel.setText("No OCR result.");
            }
        });

        pipeline.getFrameStatsProperty().addListener((obs, oldVal, s) -> {
            if (s == null) return;
            long ms = s.tessOcrMs();
            if (ms >= 0) {
                String txt = pipeline.getTessOcrProperty().get();
                int chars = txt == null ? 0 : txt.length();
                statusLabel.setText("Last OCR: " + ms + " ms  (" + chars + " chars)");
            }
        });
    }
}
