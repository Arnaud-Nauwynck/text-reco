package fr.an.textreco.ui.tab;

import de.saxsys.mvvmfx.JavaView;
import fr.an.textreco.ui.viewmodel.ResultTextViewModel;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import lombok.Getter;

public class ResultTextView extends BorderPane implements JavaView<ResultTextViewModel> {

    @Getter
    private final BorderPane root = new BorderPane();

    private final TextArea textArea = new TextArea();

    public ResultTextView(ResultTextViewModel viewModel) {
        Label title = new Label("Recognised Text");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setStyle("-fx-text-fill: #aaaaff;");

        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-control-inner-background: #2d2d2d; -fx-text-fill: #eeeeee; -fx-font-family: monospace; -fx-font-size: 13;");
        textArea.setPromptText("Recognised text will appear here…");

        textArea.textProperty().bind(viewModel.ocrProperty());

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            textArea.textProperty().unbind();
            textArea.clear();
            textArea.textProperty().bind(viewModel.ocrProperty());
        });
        clearBtn.setStyle("-fx-background-color: #444; -fx-text-fill: #ddd;");

        HBox toolbar = new HBox(8, clearBtn);
        toolbar.setPadding(new Insets(4, 0, 4, 0));

        VBox top = new VBox(6, title, toolbar);
        top.setPadding(new Insets(16, 16, 4, 16));

        root.setTop(top);
        root.setCenter(textArea);
        BorderPane.setMargin(textArea, new Insets(0, 16, 16, 16));
        root.setStyle("-fx-background-color: #1e1e1e;");
    }
}
