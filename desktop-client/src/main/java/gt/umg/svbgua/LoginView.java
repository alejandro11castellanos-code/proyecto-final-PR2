package gt.umg.svbgua;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

/** Pantalla de acceso: panel de marca a la izquierda y formulario a la derecha. */
final class LoginView extends HBox {

    private final AuthClient authClient;
    private final BiConsumer<String, AuthClient.Usuario> alIngresar;
    private final String servidorActual;
    private final Consumer<String> alCambiarServidor;

    private final TextField usuario = new TextField();
    private final PasswordField contrasena = new PasswordField();
    private final Label mensaje = new Label();
    private final Button ingresar = new Button("Ingresar");
    private final VBox formulario = new VBox(16);

    LoginView(AuthClient authClient, String servidorActual, Consumer<String> alCambiarServidor,
            BiConsumer<String, AuthClient.Usuario> alIngresar) {
        this.authClient = authClient;
        this.alIngresar = alIngresar;
        this.servidorActual = servidorActual;
        this.alCambiarServidor = alCambiarServidor;

        StackPane marca = construirPanelDeMarca();
        HBox.setHgrow(marca, Priority.ALWAYS);
        marca.setPrefWidth(430);
        marca.setMinWidth(380);

        VBox lado = construirFormulario();
        HBox.setHgrow(lado, Priority.ALWAYS);
        lado.setPrefWidth(470);

        getChildren().addAll(marca, lado);

        Platform.runLater(() -> {
            Animaciones.entradaEnCascada(formulario.getChildren());
            usuario.requestFocus();
        });
    }

    private StackPane construirPanelDeMarca() {
        Label titulo = new Label("SVB-GUA");
        titulo.getStyleClass().add("login-title");
        Label eslogan = new Label("Sistema de Venta Local de Boletos");
        eslogan.getStyleClass().add("login-tagline");

        VBox puntos = new VBox(8,
                punto("Venta rápida y sin sobreventa"),
                punto("Boletos con código QR por correo"),
                punto("Reportes y gráficas en PDF"));
        puntos.setPadding(new Insets(26, 0, 0, 0));

        VBox texto = new VBox(6, titulo, eslogan, puntos);
        texto.setAlignment(Pos.CENTER_LEFT);
        texto.setPadding(new Insets(0, 44, 0, 52));

        StackPane panel = new StackPane(decoracion(190, -150, -170, 0.10), decoracion(120, 170, 190, 0.12),
                decoracion(70, 150, -190, 0.09), texto);
        panel.getStyleClass().add("login-brand");
        StackPane.setAlignment(texto, Pos.CENTER_LEFT);
        Animaciones.entrada(texto, 0);
        return panel;
    }

    private static Label punto(String texto) {
        Label etiqueta = new Label("•  " + texto);
        etiqueta.getStyleClass().add("login-point");
        return etiqueta;
    }

    // Círculos translúcidos que flotan despacio detrás del texto.
    private static Circle decoracion(double radio, double x, double y, double opacidad) {
        Circle circulo = new Circle(radio, Color.web("#ffffff", opacidad));
        circulo.setTranslateX(x);
        circulo.setTranslateY(y);
        circulo.setMouseTransparent(true);
        TranslateTransition flotar = new TranslateTransition(Duration.seconds(5 + radio / 40), circulo);
        flotar.setByY(22);
        flotar.setAutoReverse(true);
        flotar.setCycleCount(TranslateTransition.INDEFINITE);
        flotar.setInterpolator(Interpolator.EASE_BOTH);
        flotar.play();
        return circulo;
    }

    private VBox construirFormulario() {
        Label bienvenida = new Label("Bienvenido");
        bienvenida.getStyleClass().add("login-welcome");
        Label subtitulo = new Label("Ingrese a su cuenta para continuar");
        subtitulo.getStyleClass().add("muted");

        usuario.setPromptText("Usuario");
        contrasena.setPromptText("Contraseña");

        ingresar.setDefaultButton(true);
        ingresar.setMaxWidth(Double.MAX_VALUE);
        ingresar.setPadding(new Insets(11, 18, 11, 18));
        ingresar.setOnAction(evento -> iniciarSesion());

        mensaje.setWrapText(true);
        Tema.ocultarSiVacia(mensaje);

        formulario.getChildren().addAll(new VBox(4, bienvenida, subtitulo), campo("Usuario", usuario),
                campo("Contraseña", contrasena), ingresar, mensaje);
        formulario.getStyleClass().add("card");
        formulario.setPadding(new Insets(34));
        formulario.setMaxWidth(370);
        formulario.setMaxHeight(StackPane.USE_PREF_SIZE);

        VBox pie = construirPieServidor();
        // Columna: la tarjeta se centra en el espacio libre y el pie queda debajo sin pisarla.
        Region arriba = new Region();
        Region abajo = new Region();
        VBox.setVgrow(arriba, Priority.ALWAYS);
        VBox.setVgrow(abajo, Priority.ALWAYS);
        VBox lado = new VBox(arriba, formulario, abajo, pie);
        lado.setAlignment(Pos.CENTER);
        lado.getStyleClass().add("login-form-bg");
        lado.setPadding(new Insets(30));
        return lado;
    }

    // Quien instala la app no tiene el servidor en su máquina: la dirección se elige aquí y se recuerda.
    private VBox construirPieServidor() {
        Label actual = new Label("Servidor: " + servidorActual);
        actual.getStyleClass().add("muted");
        actual.setStyle("-fx-font-size: 11px;");
        Button cambiar = new Button("Cambiar");
        cambiar.getStyleClass().add("secondary");
        cambiar.setStyle("-fx-padding: 3 10; -fx-font-size: 11px;");
        HBox fila = new HBox(8, actual, cambiar);
        fila.setAlignment(Pos.CENTER);

        TextField direccion = new TextField(servidorActual);
        direccion.setPromptText("http://192.168.1.10:3000");
        direccion.setMaxWidth(380);
        Button guardar = new Button("Probar y guardar");
        Button cancelar = new Button("Cancelar");
        cancelar.getStyleClass().add("secondary");
        guardar.setMinWidth(Region.USE_PREF_SIZE);
        cancelar.setMinWidth(Region.USE_PREF_SIZE);
        HBox botones = new HBox(8, guardar, cancelar);
        botones.setAlignment(Pos.CENTER);
        VBox edicion = new VBox(8, direccion, botones);
        edicion.setAlignment(Pos.CENTER);
        Label estado = new Label();
        estado.setWrapText(true);
        estado.setMaxWidth(440);
        Tema.ocultarSiVacia(estado);

        VBox cuerpo = new VBox(8, edicion, estado);
        cuerpo.setAlignment(Pos.CENTER);
        cuerpo.setManaged(false);
        cuerpo.setVisible(false);

        cambiar.setOnAction(evento -> {
            cuerpo.setManaged(true);
            cuerpo.setVisible(true);
            direccion.requestFocus();
        });
        cancelar.setOnAction(evento -> {
            estado.setText("");
            cuerpo.setManaged(false);
            cuerpo.setVisible(false);
        });
        guardar.setOnAction(evento -> {
            guardar.setDisable(true);
            estado.getStyleClass().remove("error-label");
            estado.setText("Comprobando...");
            Async.run(
                    () -> ConfiguracionServidor.probarYGuardar(direccion.getText()),
                    url -> alCambiarServidor.accept(url),
                    error -> {
                        guardar.setDisable(false);
                        estado.getStyleClass().add("error-label");
                        estado.setText(error);
                    });
        });
        direccion.setOnAction(guardar.getOnAction());

        VBox pie = new VBox(8, fila, cuerpo);
        pie.setAlignment(Pos.CENTER);
        return pie;
    }

    private static VBox campo(String etiqueta, javafx.scene.control.Control control) {
        Label label = new Label(etiqueta);
        label.getStyleClass().add("field-label");
        return new VBox(6, label, control);
    }

    private void iniciarSesion() {
        String nombre = usuario.getText();
        String clave = contrasena.getText();
        if (nombre == null || nombre.isBlank() || clave == null || clave.isBlank()) {
            avisarError("Ingrese su usuario y contraseña.");
            return;
        }

        ingresar.setDisable(true);
        ingresar.setText("Verificando...");
        ingresar.setGraphic(spinner());
        mensaje.getStyleClass().remove("error-label");
        mensaje.setText("");

        CompletableFuture
                .supplyAsync(() -> authClient.login(nombre.trim(), clave))
                .whenComplete((resultado, error) -> Platform.runLater(() -> {
                    ingresar.setDisable(false);
                    ingresar.setText("Ingresar");
                    ingresar.setGraphic(null);
                    if (error != null) {
                        Throwable causa = error.getCause() == null ? error : error.getCause();
                        avisarError(causa.getMessage());
                        return;
                    }
                    alIngresar.accept(resultado.token(), resultado.usuario());
                }));
    }

    private static ProgressIndicator spinner() {
        ProgressIndicator indicador = new ProgressIndicator();
        indicador.setPrefSize(16, 16);
        indicador.setStyle("-fx-progress-color: white;");
        return indicador;
    }

    private void avisarError(String texto) {
        mensaje.setText(texto);
        if (!mensaje.getStyleClass().contains("error-label")) {
            mensaje.getStyleClass().add("error-label");
        }
        Animaciones.sacudir(formulario);
    }
}
