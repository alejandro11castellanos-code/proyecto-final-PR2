package gt.umg.svbgua;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Piezas para armar formularios de alta/edición como tarjeta junto a la tabla. */
final class Formulario {

    private static final double ANCHO_TARJETA = 340;

    private Formulario() {
    }

    /** Tabla (o lo que sea) a la izquierda y la tarjeta del formulario a la derecha. */
    static HBox lado(Node principal, Node tarjeta) {
        HBox.setHgrow(principal, Priority.ALWAYS);
        return new HBox(16, principal, tarjeta);
    }

    static VBox tarjeta(Label titulo, String ayuda, Node... contenido) {
        titulo.getStyleClass().add("section-title");
        Label ayudaLabel = new Label(ayuda);
        ayudaLabel.getStyleClass().add("muted");
        ayudaLabel.setStyle("-fx-font-size: 11px;");
        ayudaLabel.setWrapText(true);

        VBox cuerpo = new VBox(12, contenido);
        VBox tarjeta = new VBox(14, new VBox(2, titulo, ayudaLabel), cuerpo);
        tarjeta.getStyleClass().add("card");
        tarjeta.setPadding(new Insets(18));
        tarjeta.setMinWidth(ANCHO_TARJETA);
        tarjeta.setPrefWidth(ANCHO_TARJETA);
        tarjeta.setMaxWidth(ANCHO_TARJETA);
        // Sin esto la tarjeta se estiraría a toda la altura de la tabla.
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        return tarjeta;
    }

    static VBox campo(String etiqueta, Control control) {
        Label label = new Label(etiqueta);
        label.getStyleClass().add("field-label");
        control.setMaxWidth(Double.MAX_VALUE);
        return new VBox(5, label, control);
    }

    static HBox dosColumnas(Node izquierda, Node derecha) {
        HBox.setHgrow(izquierda, Priority.ALWAYS);
        HBox.setHgrow(derecha, Priority.ALWAYS);
        HBox fila = new HBox(10, izquierda, derecha);
        // Reparto parejo aunque un lado tenga contenido más ancho.
        for (Node lado : new Node[] {izquierda, derecha}) {
            ((Region) lado).setPrefWidth(0);
            ((Region) lado).setMaxWidth(Double.MAX_VALUE);
        }
        return fila;
    }

    /** La primera acción es la principal; el resto se estila aparte (secondary/danger) en cada panel. */
    static FlowPane botones(Button... botones) {
        FlowPane fila = new FlowPane(8, 8, botones);
        fila.setPadding(new Insets(4, 0, 0, 0));
        return fila;
    }
}
