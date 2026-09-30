package gt.umg.svbgua;

import java.util.Locale;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Simulador local de pago con tarjeta. Valida el número (Luhn), el
 * vencimiento y el CVV, y tras una espera falsa "aprueba" el pago. Nunca
 * sale de la app ni toca ningún servicio externo — el enunciado excluye
 * explícitamente una pasarela de pago real (ver README, "No Incluye").
 */
public final class PagoDialog {

    private PagoDialog() {
    }

    /** Abre el diálogo; si el pago simulado se aprueba, corre {@code alAprobar}. */
    public static void mostrar(Window owner, double monto, Runnable alAprobar) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Pago con tarjeta");

        Label titulo = new Label("Pago con tarjeta (simulado)");
        titulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label montoLabel = new Label(formatearMoneda(monto));
        montoLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        TextField numeroField = new TextField();
        numeroField.setPromptText("Número de tarjeta");

        Label marcaLabel = new Label(" ");
        marcaLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        numeroField.textProperty().addListener((obs, previo, actual) -> {
            String marca = CardValidator.marca(actual);
            marcaLabel.setText(marca.isEmpty() ? " " : marca);
        });

        TextField nombreField = new TextField();
        nombreField.setPromptText("Nombre en la tarjeta");

        TextField vencimientoField = new TextField();
        vencimientoField.setPromptText("MM/AA");
        vencimientoField.setMaxWidth(90);

        PasswordField cvvField = new PasswordField();
        cvvField.setPromptText("CVV");
        cvvField.setMaxWidth(70);

        HBox vencimientoYCvv = new HBox(8, vencimientoField, cvvField);

        Label status = new Label();
        status.setWrapText(true);

        Button pagarButton = new Button("Pagar " + formatearMoneda(monto));
        pagarButton.setDefaultButton(true);
        Button cancelarButton = new Button("Cancelar");
        cancelarButton.setOnAction(event -> stage.close());

        pagarButton.setOnAction(event -> intentarPago(
                stage, numeroField.getText(), nombreField.getText(), vencimientoField.getText(),
                cvvField.getText(), status, pagarButton, cancelarButton, alAprobar));

        VBox root = new VBox(10,
                titulo, montoLabel, numeroField, marcaLabel, nombreField,
                vencimientoYCvv, new HBox(8, pagarButton, cancelarButton), status);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER_LEFT);

        stage.setScene(new Scene(root, 340, 430));
        stage.show();
    }

    private static void intentarPago(Stage stage, String numero, String nombre, String vencimiento,
            String cvv, Label status, Button pagarButton, Button cancelarButton, Runnable alAprobar) {
        if (nombre == null || nombre.isBlank()) {
            status.setText("Ingresá el nombre en la tarjeta.");
            return;
        }
        if (!CardValidator.luhnValido(numero)) {
            status.setText("El número de tarjeta no es válido.");
            return;
        }
        if (!CardValidator.vencimientoValido(vencimiento)) {
            status.setText("La fecha de vencimiento no es válida o ya venció.");
            return;
        }
        if (!CardValidator.cvvValido(cvv)) {
            status.setText("El CVV debe tener 3 o 4 dígitos.");
            return;
        }

        pagarButton.setDisable(true);
        cancelarButton.setDisable(true);
        status.setText("Procesando pago...");

        PauseTransition procesando = new PauseTransition(Duration.millis(1200));
        procesando.setOnFinished(event -> {
            status.setText("Pago aprobado ✓");
            PauseTransition cierre = new PauseTransition(Duration.millis(500));
            cierre.setOnFinished(ev -> {
                stage.close();
                alAprobar.run();
            });
            cierre.play();
        });
        procesando.play();
    }

    private static String formatearMoneda(double valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", valor);
    }
}
