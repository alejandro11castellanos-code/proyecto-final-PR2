package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Boleto;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Ventana modal que muestra el QR de cada ítem de una venta recién
 * confirmada y permite mandarlos por correo.
 */
public final class BoletosDialog {

    private BoletosDialog() {
    }

    public static void mostrar(Window owner, CatalogClient client, String token, int idVenta) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Boletos — venta #" + idVenta);

        VBox boletosBox = new VBox(12);
        boletosBox.setPadding(new Insets(16));
        boletosBox.getChildren().add(new Label("Generando códigos QR..."));

        ScrollPane scroll = new ScrollPane(boletosBox);
        scroll.setFitToWidth(true);

        TextField emailField = new TextField();
        emailField.setPromptText("correo@ejemplo.com");
        emailField.setPrefWidth(220);
        Button enviarButton = new Button("Enviar por correo");
        Label status = new Label();
        status.setWrapText(true);

        HBox envio = new HBox(8, emailField, enviarButton);
        envio.setAlignment(Pos.CENTER_LEFT);

        VBox pieDePagina = new VBox(8, envio, status);
        pieDePagina.setPadding(new Insets(0, 16, 16, 16));

        BorderPane root = new BorderPane();
        root.setCenter(scroll);
        root.setBottom(pieDePagina);

        stage.setScene(new Scene(root, 420, 540));
        stage.show();

        Async.run(
                () -> client.listBoletos(token, idVenta),
                boletos -> mostrarBoletos(boletosBox, boletos),
                error -> {
                    boletosBox.getChildren().setAll(new Label(error));
                });

        enviarButton.setOnAction(event -> {
            String email = emailField.getText();
            if (email == null || email.isBlank()) {
                status.setText("Ingresá un correo destino.");
                return;
            }
            String destinatario = email.trim();
            enviarButton.setDisable(true);
            status.setText("Enviando...");
            Async.run(
                    () -> client.enviarBoletos(token, idVenta, destinatario),
                    () -> {
                        enviarButton.setDisable(false);
                        status.setText("Boletos enviados a " + destinatario + ".");
                    },
                    error -> {
                        enviarButton.setDisable(false);
                        status.setText(error);
                    });
        });
    }

    private static void mostrarBoletos(VBox contenedor, List<Boleto> boletos) {
        if (boletos.isEmpty()) {
            contenedor.getChildren().setAll(new Label("Esta venta no tiene boletos."));
            return;
        }
        contenedor.getChildren().setAll(boletos.stream().map(BoletosDialog::tarjeta).toList());
    }

    private static Node tarjeta(Boleto boleto) {
        Label titulo = new Label(
                boleto.tituloEvento() + "\n" + boleto.nombreLocalidad() + " × " + boleto.cantidad());
        titulo.setWrapText(true);
        titulo.setStyle("-fx-font-weight: bold;");

        ImageView imagen = new ImageView(decodificarQr(boleto.qr()));
        imagen.setFitWidth(160);
        imagen.setFitHeight(160);

        Label codigo = new Label(boleto.codigo());
        codigo.setStyle("-fx-font-size: 10px;");

        VBox tarjeta = new VBox(6, titulo, imagen, codigo);
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setPadding(new Insets(8));
        tarjeta.setStyle("-fx-border-color: derive(-fx-base, -20%); -fx-border-radius: 4; -fx-border-width: 1;");
        return tarjeta;
    }

    private static Image decodificarQr(String dataUrl) {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }
}
