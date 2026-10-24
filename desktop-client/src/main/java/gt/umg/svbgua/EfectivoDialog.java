package gt.umg.svbgua;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Cobro en efectivo: muestra el total, el vendedor escribe cuánto le entregó el cliente y el diálogo calcula
 * el cambio al instante. No deja cobrar si el monto no alcanza.
 */
public final class EfectivoDialog {

    private EfectivoDialog() {
    }

    /** Abre el diálogo; al cobrar, cierra y corre {@code alCobrar} con el cambio a devolver. */
    public static void mostrar(Window owner, double monto, Consumer<BigDecimal> alCobrar) {
        BigDecimal total = CobroEfectivo.aQuetzales(monto);

        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Pago en efectivo");

        Label titulo = new Label("Pago en efectivo");
        titulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label totalTitulo = new Label("Total a cobrar");
        totalTitulo.getStyleClass().add("kpi-title");
        Label totalLabel = new Label(formatearMoneda(total));
        totalLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField recibidoField = new TextField();
        recibidoField.setPromptText("Monto recibido (Q)");
        Button exactoButton = new Button("Monto exacto");
        exactoButton.getStyleClass().add("secondary");
        exactoButton.setOnAction(event -> recibidoField.setText(total.toPlainString()));
        HBox recibidoFila = new HBox(8, recibidoField, exactoButton);
        recibidoFila.setAlignment(Pos.CENTER_LEFT);

        Label resultado = new Label(" ");
        resultado.setWrapText(true);
        resultado.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        Button cobrarButton = new Button("Cobrar " + formatearMoneda(total));
        cobrarButton.setDefaultButton(true);
        cobrarButton.setDisable(true);
        Button cancelarButton = new Button("Cancelar");
        cancelarButton.getStyleClass().add("secondary");
        cancelarButton.setOnAction(event -> stage.close());

        // Se recalcula con cada tecla: el cambio y el botón siempre reflejan lo escrito.
        recibidoField.textProperty().addListener((obs, previo, actual) -> {
            var recibido = CobroEfectivo.interpretar(actual);
            if (recibido.isEmpty()) {
                resultado.setText(actual == null || actual.isBlank() ? " " : "Escribí un monto válido (por ejemplo 150 o 150.50).");
                resultado.setStyle("-fx-font-size: 13px; -fx-text-fill: #b4443f;");
                cobrarButton.setDisable(true);
            } else if (CobroEfectivo.alcanza(total, recibido.get())) {
                resultado.setText("Cambio: " + formatearMoneda(CobroEfectivo.cambio(total, recibido.get())));
                resultado.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1f8a4c;");
                cobrarButton.setDisable(false);
            } else {
                resultado.setText("Faltan " + formatearMoneda(CobroEfectivo.faltante(total, recibido.get())));
                resultado.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #b4443f;");
                cobrarButton.setDisable(true);
            }
        });

        cobrarButton.setOnAction(event -> {
            BigDecimal cambio = CobroEfectivo.interpretar(recibidoField.getText())
                    .filter(recibido -> CobroEfectivo.alcanza(total, recibido))
                    .map(recibido -> CobroEfectivo.cambio(total, recibido))
                    .orElse(null);
            if (cambio == null) {
                return;
            }
            stage.close();
            alCobrar.accept(cambio);
        });

        VBox root = new VBox(10, titulo, totalTitulo, totalLabel, recibidoFila, resultado,
                new HBox(8, cobrarButton, cancelarButton));
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        Scene escena = new Scene(root, 380, 270);
        Tema.aplicar(escena);
        stage.setScene(escena);
        stage.show();
        recibidoField.requestFocus();
    }

    private static String formatearMoneda(BigDecimal valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", valor);
    }
}
