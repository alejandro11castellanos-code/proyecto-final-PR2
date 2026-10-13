package gt.umg.svbgua;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.MouseEvent;

/** Aplica el tema visual (hoja de estilos + microinteracciones de botones) a una escena. */
final class Tema {

    private static final String HOJA = Tema.class.getResource("theme.css").toExternalForm();

    private Tema() {
    }

    static void aplicar(Scene scene) {
        if (!scene.getStylesheets().contains(HOJA)) {
            scene.getStylesheets().add(HOJA);
            instalarEfectosDeBoton(scene);
        }
    }

    /** Una etiqueta sin texto no ocupa espacio (evita huecos por mensajes de estado vacíos). */
    static void ocultarSiVacia(javafx.scene.control.Label etiqueta) {
        var conTexto = etiqueta.textProperty().isNotEmpty();
        etiqueta.managedProperty().bind(conTexto);
        etiqueta.visibleProperty().bind(conTexto);
    }

    // Un solo filtro por escena cubre también los botones que se crean después.
    // El estado (hover/pressed) se lee en runLater, cuando JavaFX ya lo actualizó.
    private static void instalarEfectosDeBoton(Scene scene) {
        for (var tipo : java.util.List.of(MouseEvent.MOUSE_ENTERED_TARGET, MouseEvent.MOUSE_EXITED_TARGET,
                MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED)) {
            scene.addEventFilter(tipo, evento -> {
                ButtonBase boton = botonDe(evento.getTarget());
                if (boton != null && !boton.getStyleClass().contains("nav-button")) {
                    Platform.runLater(() -> Animaciones.escalaDeBoton(boton));
                }
            });
        }
    }

    private static ButtonBase botonDe(Object destino) {
        Node nodo = destino instanceof Node n ? n : null;
        while (nodo != null && !(nodo instanceof ButtonBase)) {
            nodo = nodo.getParent();
        }
        return (ButtonBase) nodo;
    }
}
