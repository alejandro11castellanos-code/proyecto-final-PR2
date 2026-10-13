package gt.umg.svbgua;

import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Estructura común de las pantallas autenticadas: barra lateral oscura con la
 * navegación entre secciones, tarjeta del usuario y cierre de sesión, y un área
 * de contenido que cambia con una transición.
 */
class AppShell extends BorderPane {

    record Seccion(String titulo, Node contenido) {
    }

    private final Label tituloPagina = new Label();
    private final StackPane contenido = new StackPane();

    AppShell(String modulo, String nombreCompleto, String rol, List<Seccion> secciones, Runnable cerrarSesion) {
        setLeft(construirBarraLateral(modulo, nombreCompleto, rol, secciones, cerrarSesion));

        tituloPagina.getStyleClass().add("page-title");
        HBox encabezado = new HBox(tituloPagina);
        encabezado.getStyleClass().add("shell-header");
        encabezado.setAlignment(Pos.CENTER_LEFT);

        contenido.getStyleClass().add("shell-content");
        // La barra lateral ocupa toda la altura; el encabezado y el contenido van a su derecha.
        BorderPane area = new BorderPane();
        area.setTop(encabezado);
        area.setCenter(contenido);
        setCenter(area);
    }

    private VBox construirBarraLateral(String modulo, String nombreCompleto, String rol, List<Seccion> secciones,
            Runnable cerrarSesion) {
        Label marca = new Label("SG");
        marca.getStyleClass().add("brand-mark-text");
        StackPane logo = new StackPane(marca);
        logo.getStyleClass().add("brand-mark");
        logo.setMinSize(40, 40);
        logo.setMaxSize(40, 40);

        Label nombre = new Label("SVB-GUA");
        nombre.getStyleClass().add("brand-name");
        Label submodulo = new Label(modulo);
        submodulo.getStyleClass().add("brand-sub");
        HBox marcaCompleta = new HBox(12, logo, new VBox(1, nombre, submodulo));
        marcaCompleta.setAlignment(Pos.CENTER_LEFT);
        marcaCompleta.setPadding(new Insets(0, 0, 22, 6));

        Label caption = new Label("MENÚ");
        caption.getStyleClass().add("nav-caption");
        VBox navegacion = new VBox(4);
        navegacion.getChildren().add(caption);

        ToggleGroup grupo = new ToggleGroup();
        // Siempre hay una sección activa: volver a pulsar la actual no la deselecciona.
        grupo.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null && anterior != null) {
                grupo.selectToggle(anterior);
            }
        });
        for (Seccion seccion : secciones) {
            ToggleButton boton = new ToggleButton(seccion.titulo());
            boton.getStyleClass().add("nav-button");
            boton.setMaxWidth(Double.MAX_VALUE);
            boton.setToggleGroup(grupo);
            boton.selectedProperty().addListener((obs, antes, ahora) -> {
                if (ahora) {
                    mostrar(seccion);
                }
            });
            navegacion.getChildren().add(boton);
        }

        Region espaciador = new Region();
        VBox.setVgrow(espaciador, Priority.ALWAYS);

        VBox barra = new VBox(marcaCompleta, navegacion, espaciador, construirTarjetaUsuario(nombreCompleto, rol,
                cerrarSesion));
        barra.getStyleClass().add("sidebar");
        barra.setPrefWidth(222);
        barra.setMinWidth(222);

        if (!secciones.isEmpty()) {
            ((ToggleButton) navegacion.getChildren().get(1)).setSelected(true);
        }
        return barra;
    }

    private VBox construirTarjetaUsuario(String nombreCompleto, String rol, Runnable cerrarSesion) {
        Label iniciales = new Label(iniciales(nombreCompleto));
        iniciales.getStyleClass().add("avatar-text");
        StackPane avatar = new StackPane(iniciales);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(38, 38);
        avatar.setMaxSize(38, 38);

        Label nombre = new Label(nombreCompleto);
        nombre.getStyleClass().add("user-name");
        nombre.setMaxWidth(128);
        Label rolLabel = new Label(rol);
        rolLabel.getStyleClass().add("user-role");

        HBox usuario = new HBox(10, avatar, new VBox(1, nombre, rolLabel));
        usuario.setAlignment(Pos.CENTER_LEFT);

        Button salir = new Button("Cerrar sesión");
        salir.getStyleClass().add("logout");
        salir.setMaxWidth(Double.MAX_VALUE);
        salir.setOnAction(evento -> cerrarSesion.run());

        VBox tarjeta = new VBox(12, usuario, salir);
        tarjeta.getStyleClass().add("user-card");
        return tarjeta;
    }

    private void mostrar(Seccion seccion) {
        tituloPagina.setText(seccion.titulo());
        contenido.getChildren().setAll(seccion.contenido());
        Animaciones.cambioDeVista(seccion.contenido());
    }

    static String iniciales(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "?";
        }
        String[] partes = nombreCompleto.trim().split("\\s+");
        String primera = partes[0].substring(0, 1);
        String segunda = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primera + segunda).toUpperCase();
    }
}
