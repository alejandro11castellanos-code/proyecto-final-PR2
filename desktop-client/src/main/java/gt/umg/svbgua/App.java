package gt.umg.svbgua;

import java.util.concurrent.CompletableFuture;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Pantalla inicial de autenticación del cliente de escritorio. */
public class App extends Application {

    private final AuthClient authClient = AuthClient.fromEnvironment();
    private final CatalogClient catalogClient = CatalogClient.fromEnvironment();

    @Override
    public void start(Stage stage) {
        Label title = new Label("Iniciar sesión");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField username = new TextField();
        username.setPromptText("Usuario");
        username.setMaxWidth(280);

        PasswordField password = new PasswordField();
        password.setPromptText("Contraseña");
        password.setMaxWidth(280);

        Label message = new Label();
        message.setWrapText(true);
        Button loginButton = new Button("Ingresar");
        loginButton.setDefaultButton(true);
        loginButton.setOnAction(event -> login(
                stage, username.getText(), password.getText(), loginButton, message));

        VBox root = new VBox(14, title, username, password, loginButton, message);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));

        stage.setScene(new Scene(root, 480, 360));
        stage.setTitle("SVB-GUA — Acceso");
        stage.show();
    }

    private void login(Stage stage, String username, String password, Button button, Label message) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            message.setText("Ingrese su usuario y contraseña.");
            return;
        }

        button.setDisable(true);
        message.setText("Verificando credenciales...");

        CompletableFuture
                .supplyAsync(() -> authClient.login(username.trim(), password))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    button.setDisable(false);
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        message.setText(cause.getMessage());
                        return;
                    }
                    onLogin(stage, result.token(), result.usuario());
                }));
    }

    private void onLogin(Stage stage, String token, AuthClient.Usuario usuario) {
        if ("administrador".equals(usuario.rol())) {
            stage.setScene(new Scene(new AdminView(catalogClient, token, usuario.nombreCompleto()), 960, 620));
            stage.setTitle("SVB-GUA — Administración");
            return;
        }

        Label bienvenida = new Label("Bienvenido, " + usuario.nombreCompleto() + " (" + usuario.rol() + ").\n"
                + "El punto de venta para vendedores llega en la fase 4.");
        bienvenida.setWrapText(true);
        VBox root = new VBox(bienvenida);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));
        stage.setScene(new Scene(root, 480, 360));
        stage.setTitle("SVB-GUA — " + usuario.nombreCompleto());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
