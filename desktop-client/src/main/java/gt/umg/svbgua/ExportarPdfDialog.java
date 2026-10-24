package gt.umg.svbgua;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Antes de exportar el PDF: qué secciones incluir. Todas vienen marcadas; se pueden quitar las que no hacen
 * falta, y no deja exportar sin ninguna. La portada (KPIs y filtros) va siempre.
 */
public final class ExportarPdfDialog {

    private ExportarPdfDialog() {
    }

    /**
     * @param pivote la tabla cruzada que se está viendo; null si no cargó, y entonces no se ofrece
     * @param alExportar recibe las secciones elegidas (en orden de {@link SeccionPdf}) al confirmar
     */
    public static void mostrar(Window owner, Pivote pivote, Consumer<Set<SeccionPdf>> alExportar) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Exportar PDF");

        Label titulo = new Label("¿Qué incluir en el PDF?");
        titulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Label nota = new Label("La portada con los totales y los filtros aplicados siempre se incluye.");
        nota.getStyleClass().add("muted");
        nota.setWrapText(true);

        Map<SeccionPdf, CheckBox> casillas = new EnumMap<>(SeccionPdf.class);
        VBox lista = new VBox(8);
        for (SeccionPdf seccion : SeccionPdf.values()) {
            if (seccion == SeccionPdf.TABLA_CRUZADA && pivote == null) {
                continue;
            }
            String texto = seccion == SeccionPdf.TABLA_CRUZADA
                    ? "Tabla cruzada: " + pivote.titulo()
                    : seccion.titulo();
            CheckBox casilla = new CheckBox(texto);
            casilla.setSelected(true);
            casillas.put(seccion, casilla);
            lista.getChildren().add(casilla);
        }

        Button todoButton = new Button("Marcar todo");
        todoButton.getStyleClass().add("secondary");
        todoButton.setOnAction(event -> casillas.values().forEach(casilla -> casilla.setSelected(true)));
        Button ningunoButton = new Button("Quitar todo");
        ningunoButton.getStyleClass().add("secondary");
        ningunoButton.setOnAction(event -> casillas.values().forEach(casilla -> casilla.setSelected(false)));

        Label aviso = new Label(" ");
        aviso.setStyle("-fx-text-fill: #b4443f;");

        Button exportarButton = new Button("Exportar");
        exportarButton.setDefaultButton(true);
        Button cancelarButton = new Button("Cancelar");
        cancelarButton.getStyleClass().add("secondary");
        cancelarButton.setOnAction(event -> stage.close());

        // Sin ninguna sección el PDF sería solo la portada: se avisa y se bloquea el botón.
        Runnable revisar = () -> {
            boolean alguna = casillas.values().stream().anyMatch(CheckBox::isSelected);
            exportarButton.setDisable(!alguna);
            aviso.setText(alguna ? " " : "Elegí al menos una sección.");
        };
        casillas.values().forEach(casilla -> casilla.selectedProperty().addListener((obs, antes, ahora) -> revisar.run()));

        exportarButton.setOnAction(event -> {
            Set<SeccionPdf> elegidas = EnumSet.noneOf(SeccionPdf.class);
            casillas.forEach((seccion, casilla) -> {
                if (casilla.isSelected()) {
                    elegidas.add(seccion);
                }
            });
            if (elegidas.isEmpty()) {
                return;
            }
            stage.close();
            alExportar.accept(elegidas);
        });

        HBox seleccion = new HBox(8, todoButton, ningunoButton);
        HBox acciones = new HBox(8, exportarButton, cancelarButton);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, titulo, nota, lista, seleccion, aviso, acciones);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        Scene escena = new Scene(root, 440, 400);
        Tema.aplicar(escena);
        stage.setScene(escena);
        stage.show();
    }
}
