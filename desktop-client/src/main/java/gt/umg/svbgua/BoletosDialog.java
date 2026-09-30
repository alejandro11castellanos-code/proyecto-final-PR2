package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Boleto;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Ventana modal que muestra los boletos de una venta recién confirmada como
 * si fueran boletos de verdad — banner con la foto del artista (la misma
 * fuente que usa el "▶ Escuchar" de Conciertos/Ventas, sin llamada nueva),
 * línea de perforación y el QR abajo — y permite mandarlos por correo.
 */
public final class BoletosDialog {

    private static final double ANCHO_TICKET = 220;
    private static final double ALTO_BANNER = 90;

    private BoletosDialog() {
    }

    public static void mostrar(Window owner, CatalogClient client, String token, int idVenta) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Boletos — venta #" + idVenta);

        VBox boletosBox = new VBox(16);
        boletosBox.setAlignment(Pos.TOP_CENTER);
        boletosBox.setPadding(new Insets(16));
        boletosBox.getChildren().add(new Label("Generando boletos..."));

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
        root.setOpacity(0);
        root.setScaleX(0.94);
        root.setScaleY(0.94);

        stage.setScene(new Scene(root, 320, 560));
        stage.show();

        FadeTransition aparecer = new FadeTransition(Duration.millis(220), root);
        aparecer.setToValue(1);
        ScaleTransition agrandar = new ScaleTransition(Duration.millis(220), root);
        agrandar.setToX(1);
        agrandar.setToY(1);
        aparecer.play();
        agrandar.play();

        Async.run(
                () -> cargarBoletosConImagen(client, token, idVenta),
                boletos -> mostrarBoletos(boletosBox, boletos),
                error -> boletosBox.getChildren().setAll(new Label(error)));

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

    /**
     * Trae los boletos y, por cada artista distinto entre ellos, busca su
     * foto una sola vez (cachea por nombre) — todo en el hilo de fondo, para
     * que la UI solo reciba datos ya resueltos.
     */
    private static List<BoletoConImagen> cargarBoletosConImagen(CatalogClient client, String token, int idVenta) {
        List<Boleto> boletos = client.listBoletos(token, idVenta);
        PreviewClient previewClient = PreviewClient.crear();
        Map<String, String> imagenPorArtista = new HashMap<>();
        List<BoletoConImagen> resultado = new ArrayList<>();

        for (Boleto boleto : boletos) {
            String imagenUrl = imagenPorArtista.computeIfAbsent(boleto.nombreArtistico(), nombre -> {
                PreviewClient.Preview preview = previewClient.buscarPreview(nombre);
                return preview == null ? null : preview.imagenUrl();
            });
            resultado.add(new BoletoConImagen(boleto, imagenUrl));
        }
        return resultado;
    }

    private static void mostrarBoletos(VBox contenedor, List<BoletoConImagen> boletos) {
        if (boletos.isEmpty()) {
            contenedor.getChildren().setAll(new Label("Esta venta no tiene boletos."));
            return;
        }
        contenedor.getChildren().setAll(boletos.stream().map(BoletosDialog::ticket).toList());
    }

    private static Node ticket(BoletoConImagen item) {
        Boleto boleto = item.boleto();

        Region fondo = new Region();
        fondo.setPrefSize(ANCHO_TICKET, ALTO_BANNER);
        String estiloFondo = item.imagenUrl() != null
                ? "-fx-background-image: url('" + item.imagenUrl() + "'); "
                        + "-fx-background-size: cover; -fx-background-position: center center;"
                : "-fx-background-color: linear-gradient(to bottom right, #4b2e83, #1a1a2e);";
        fondo.setStyle(estiloFondo + " -fx-background-radius: 12 12 0 0;");

        Region degradado = new Region();
        degradado.setPrefSize(ANCHO_TICKET, ALTO_BANNER);
        degradado.setStyle("-fx-background-color: linear-gradient(to top, rgba(0,0,0,0.8), transparent 65%); "
                + "-fx-background-radius: 12 12 0 0;");

        Label tituloEvento = new Label(boleto.tituloEvento());
        tituloEvento.setWrapText(true);
        tituloEvento.setMaxWidth(ANCHO_TICKET - 20);
        tituloEvento.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 3, 0.6, 0, 1);");

        StackPane banner = new StackPane(fondo, degradado, tituloEvento);
        banner.setPrefSize(ANCHO_TICKET, ALTO_BANNER);
        banner.setMaxSize(ANCHO_TICKET, ALTO_BANNER);
        StackPane.setAlignment(tituloEvento, Pos.BOTTOM_LEFT);
        StackPane.setMargin(tituloEvento, new Insets(0, 8, 8, 10));

        Line perforacion = new Line(0, 0, ANCHO_TICKET - 20, 0);
        perforacion.getStrokeDashArray().addAll(5d, 4d);
        perforacion.setStyle("-fx-stroke: derive(-fx-base, -30%);");
        VBox.setMargin(perforacion, new Insets(10, 0, 0, 0));

        Label localidad = new Label(boleto.nombreLocalidad() + "  ×" + boleto.cantidad());
        localidad.setStyle("-fx-font-weight: bold;");

        ImageView qr = new ImageView(decodificarQr(boleto.qr()));
        qr.setFitWidth(140);
        qr.setFitHeight(140);

        Label codigo = new Label(boleto.codigo());
        codigo.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");

        VBox cuerpo = new VBox(8, localidad, qr, codigo);
        cuerpo.setAlignment(Pos.CENTER);
        cuerpo.setPadding(new Insets(4, 12, 14, 12));

        VBox tarjeta = new VBox(banner, perforacion, cuerpo);
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setMaxWidth(ANCHO_TICKET);
        tarjeta.setStyle("-fx-background-color: -fx-control-inner-background; -fx-background-radius: 12; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 12, 0, 0, 4);");
        return tarjeta;
    }

    private static Image decodificarQr(String dataUrl) {
        String base64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
        return new Image(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    /** Un boleto ya emparejado con la imagen de su artista (o {@code null} si no se encontró). */
    private record BoletoConImagen(Boleto boleto, String imagenUrl) {
    }
}
