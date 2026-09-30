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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Pantalla inicial de autenticación del cliente de escritorio. */
public class App extends Application {

    private final AuthClient authClient = AuthClient.fromEnvironment();
    private final CatalogClient catalogClient = CatalogClient.fromEnvironment();

    @Override
    public void start(Stage stage) {
        mostrarLogin(stage);
        stage.show();
    }

    /**
     * Pantalla de login. Se puede volver a llamar (desde "Cerrar sesión") sin
     * reiniciar la app — reemplaza la escena del mismo Stage.
     */
    private void mostrarLogin(Stage stage) {
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
        Runnable cerrarSesion = () -> mostrarLogin(stage);

        if ("administrador".equals(usuario.rol())) {
            stage.setScene(new Scene(
                    new AdminView(catalogClient, token, usuario.nombreCompleto(), cerrarSesion), 960, 620));
            stage.setTitle("SVB-GUA — Administración");
            return;
        }

        Label header = new Label("Punto de venta — " + usuario.nombreCompleto());
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Region espaciador = new Region();
        HBox.setHgrow(espaciador, Priority.ALWAYS);

        Button cerrarSesionButton = new Button("Cerrar sesión");
        cerrarSesionButton.setOnAction(event -> cerrarSesion.run());

        HBox barra = new HBox(header, espaciador, cerrarSesionButton);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(16, 16, 0, 16));

        Tab ventaTab = new Tab("Vender", new VentaPane(catalogClient, token));
        ventaTab.setClosable(false);
        Tab historialTab = new Tab("Mis ventas", new HistorialPane(catalogClient, token, false));
        historialTab.setClosable(false);
        TabPane tabs = new TabPane(ventaTab, historialTab);

        BorderPane root = new BorderPane();
        root.setTop(barra);
        root.setCenter(tabs);

        stage.setScene(new Scene(root, 900, 620));
        stage.setTitle("SVB-GUA — Punto de venta");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
