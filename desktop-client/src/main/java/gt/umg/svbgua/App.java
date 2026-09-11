package gt.umg.svbgua;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * JavaFX entry point. Phase 0 shows a placeholder window; the login view arrives
 * in phase 1.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        StackPane root = new StackPane(new Label("SVB-GUA — cliente de escritorio"));
        stage.setScene(new Scene(root, 480, 320));
        stage.setTitle("SVB-GUA");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
