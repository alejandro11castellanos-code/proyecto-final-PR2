package gt.umg.svbgua;

import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Cliente de escritorio: login y, según el rol, panel de administración o punto de venta. */
public class App extends Application {

    private final AuthClient authClient = AuthClient.fromEnvironment();
    private final CatalogClient catalogClient = CatalogClient.fromEnvironment();

    @Override
    public void start(Stage stage) {
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        mostrarLogin(stage);
        stage.show();
    }

    /**
     * Pantalla de login. Se puede volver a llamar (desde "Cerrar sesión") sin
     * reiniciar la app — reemplaza la escena del mismo Stage.
     */
    private void mostrarLogin(Stage stage) {
        LoginView login = new LoginView(authClient, (token, usuario) -> onLogin(stage, token, usuario));
        cambiarEscena(stage, login, 940, 580, "SVB-GUA — Acceso");
    }

    private void onLogin(Stage stage, String token, AuthClient.Usuario usuario) {
        Runnable cerrarSesion = () -> mostrarLogin(stage);

        if ("administrador".equals(usuario.rol())) {
            cambiarEscena(stage, new AdminView(catalogClient, token, usuario.nombreCompleto(), cerrarSesion),
                    1200, 720, "SVB-GUA — Administración");
            return;
        }
        if ("acceso".equals(usuario.rol())) {
            cambiarEscena(stage, new AccesoView(catalogClient, token, usuario.nombreCompleto(), cerrarSesion),
                    1200, 720, "SVB-GUA — Control de acceso");
            return;
        }
        cambiarEscena(stage, new VendedorView(catalogClient, token, usuario.nombreCompleto(), cerrarSesion),
                1200, 720, "SVB-GUA — Punto de venta");
    }

    private static void cambiarEscena(Stage stage, Parent raiz, double ancho, double alto, String titulo) {
        Scene escena = new Scene(raiz, ancho, alto);
        Tema.aplicar(escena);
        stage.setScene(escena);
        stage.setTitle(titulo);
        stage.centerOnScreen();

        raiz.setOpacity(0);
        FadeTransition aparecer = new FadeTransition(Duration.millis(320), raiz);
        aparecer.setToValue(1);
        aparecer.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
