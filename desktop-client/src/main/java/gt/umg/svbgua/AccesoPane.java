package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Ingreso;
import gt.umg.svbgua.CatalogClient.ResumenAcceso;
import java.util.List;
import java.util.Locale;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Control de acceso: simula la puerta del concierto. Se escribe, pega o escanea (un lector USB
 * "teclea" el código y pulsa Enter) el código de un boleto; el servidor lo marca como usado una
 * sola vez. Muestra la asistencia por concierto y los últimos ingresos, y se refresca sola para
 * reflejar también los escaneos hechos desde otro dispositivo.
 */
public final class AccesoPane extends BorderPane {

    private static final double ANCHO_RESUMEN = 300;

    private final CatalogClient client;
    private final String token;

    private final TextField codigoField = new TextField();
    private final Button registrarButton = new Button("Registrar ingreso");
    private final Label resultadoTitulo = new Label();
    private final Label resultadoDetalle = new Label();
    private final VBox resultado = new VBox(4, resultadoTitulo, resultadoDetalle);
    private final VBox resumenBox = new VBox(10);
    private final ObservableList<Ingreso> recientes = FXCollections.observableArrayList();
    private final Timeline refresco = new Timeline(new KeyFrame(Duration.seconds(5), evento -> actualizar()));

    public AccesoPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setTop(construirEscaner());
        setCenter(construirPanelesInferiores());

        // Solo se refresca mientras la pantalla está visible (fuera de una escena no hace nada).
        refresco.setCycleCount(Animation.INDEFINITE);
        sceneProperty().addListener((obs, anterior, escena) -> {
            if (escena == null) {
                refresco.stop();
            } else {
                refresco.play();
            }
        });
        actualizar();
    }

    private VBox construirEscaner() {
        Label titulo = new Label("Registrar ingreso");
        titulo.getStyleClass().add("section-title");
        Label ayuda = new Label("Escriba o pegue el código del boleto. Un lector de QR USB lo escribe solo y envía Enter.");
        ayuda.getStyleClass().add("muted");
        ayuda.setWrapText(true);

        codigoField.setPromptText("SVBGUA-V12-D34-B1-ABCD2345");
        HBox.setHgrow(codigoField, Priority.ALWAYS);
        codigoField.setOnAction(evento -> registrar());
        registrarButton.setDefaultButton(false);
        registrarButton.setOnAction(evento -> registrar());
        HBox entrada = new HBox(10, codigoField, registrarButton);
        entrada.setAlignment(Pos.CENTER_LEFT);

        resultadoTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        resultadoDetalle.setWrapText(true);
        resultado.setPadding(new Insets(12, 16, 12, 16));
        resultado.setVisible(false);
        resultado.setManaged(false);

        VBox escaner = new VBox(10, titulo, ayuda, entrada, resultado);
        escaner.getStyleClass().add("card");
        escaner.setPadding(new Insets(18));
        BorderPane.setMargin(escaner, new Insets(0, 0, 14, 0));
        return escaner;
    }

    private HBox construirPanelesInferiores() {
        Label tituloResumen = new Label("Asistencia por concierto");
        tituloResumen.getStyleClass().add("section-title");
        ScrollPane scrollResumen = new ScrollPane(resumenBox);
        scrollResumen.setFitToWidth(true);
        VBox.setVgrow(scrollResumen, Priority.ALWAYS);
        VBox izquierda = new VBox(8, tituloResumen, scrollResumen);
        izquierda.setPrefWidth(ANCHO_RESUMEN);
        izquierda.setMinWidth(ANCHO_RESUMEN);

        Label tituloRecientes = new Label("Ingresos recientes");
        tituloRecientes.getStyleClass().add("section-title");
        TableView<Ingreso> tabla = construirTabla();
        VBox.setVgrow(tabla, Priority.ALWAYS);
        VBox derecha = new VBox(8, tituloRecientes, tabla);
        HBox.setHgrow(derecha, Priority.ALWAYS);

        return new HBox(16, izquierda, derecha);
    }

    private TableView<Ingreso> construirTabla() {
        TableColumn<Ingreso, String> hora = new TableColumn<>("Hora");
        hora.setCellValueFactory(data -> new SimpleStringProperty(Fechas.fechaHora(data.getValue().fechaIngreso())));
        hora.setMinWidth(150);

        TableColumn<Ingreso, String> concierto = new TableColumn<>("Concierto");
        concierto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().tituloEvento()));
        concierto.setMinWidth(130);

        TableColumn<Ingreso, String> localidad = new TableColumn<>("Localidad");
        localidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreLocalidad()));
        localidad.setMinWidth(100);

        TableColumn<Ingreso, String> boleto = new TableColumn<>("Boleto");
        boleto.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().numero() + " de " + data.getValue().cantidad()));
        boleto.setMinWidth(70);

        TableColumn<Ingreso, String> validador = new TableColumn<>("Registró");
        validador.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().nombreValidador() == null ? "" : data.getValue().nombreValidador()));
        validador.setMinWidth(110);

        TableView<Ingreso> tabla = new TableView<>(recientes);
        tabla.getColumns().addAll(List.of(hora, concierto, localidad, boleto, validador));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPlaceholder(new Label("Aún no ha ingresado nadie."));
        return tabla;
    }

    /** Se llama al abrir la sección: refresca los datos y deja el cursor listo para escanear. */
    void alAbrir() {
        actualizar();
        codigoField.requestFocus();
    }

    void actualizar() {
        Async.run(() -> client.resumenAccesos(token), this::mostrarResumen, error -> { });
        Async.run(() -> client.ingresosRecientes(token, null), lista -> recientes.setAll(lista), error -> { });
    }

    private void registrar() {
        String codigo = codigoField.getText();
        if (codigo == null || codigo.isBlank()) {
            mostrarResultado(false, "Código vacío", "Escriba o escanee el código del boleto.");
            return;
        }
        registrarButton.setDisable(true);
        Async.run(
                () -> client.registrarIngreso(token, codigo.trim()),
                ingreso -> {
                    registrarButton.setDisable(false);
                    String boleto = ingreso.cantidad() > 1 ? "  ·  boleto " + ingreso.numero() + " de " + ingreso.cantidad() : "";
                    mostrarResultado(true, "Ingreso registrado",
                            ingreso.tituloEvento() + "  ·  " + ingreso.nombreArtistico() + "\n"
                                    + ingreso.nombreLocalidad() + boleto);
                    siguienteEscaneo();
                    actualizar();
                },
                error -> {
                    registrarButton.setDisable(false);
                    mostrarResultado(false, "Acceso denegado", error);
                    siguienteEscaneo();
                    actualizar();
                });
    }

    // Deja el campo listo para el siguiente boleto, como en una puerta real.
    private void siguienteEscaneo() {
        codigoField.clear();
        codigoField.requestFocus();
    }

    private void mostrarResultado(boolean correcto, String titulo, String detalle) {
        resultado.getStyleClass().removeAll("resultado-ok", "resultado-error");
        resultado.getStyleClass().add(correcto ? "resultado-ok" : "resultado-error");
        resultadoTitulo.setText((correcto ? "\u2713  " : "\u2717  ") + titulo);
        resultadoDetalle.setText(detalle);
        resultado.setManaged(true);
        resultado.setVisible(true);
        if (correcto) {
            Animaciones.entrada(resultado, 0);
        } else {
            Animaciones.sacudir(resultado);
        }
    }

    private void mostrarResumen(List<ResumenAcceso> filas) {
        resumenBox.getChildren().setAll(filas.stream().map(this::tarjetaResumen).toList());
    }

    private VBox tarjetaResumen(ResumenAcceso fila) {
        double porcentaje = fila.vendidos() > 0 ? (double) fila.ingresados() / fila.vendidos() : 0;

        Label titulo = new Label(fila.tituloEvento());
        titulo.setStyle("-fx-font-weight: bold;");
        titulo.setWrapText(true);
        Label artista = new Label(fila.nombreArtistico());
        artista.getStyleClass().add("muted");

        ProgressBar barra = new ProgressBar(porcentaje);
        barra.setMaxWidth(Double.MAX_VALUE);
        Label detalle = new Label(fila.ingresados() + " de " + fila.vendidos() + " ingresaron"
                + String.format(Locale.of("es", "GT"), " (%.0f%%)", porcentaje * 100));
        detalle.setStyle("-fx-font-size: 11px;");

        VBox tarjeta = new VBox(5, titulo, artista, barra, detalle);
        tarjeta.getStyleClass().add("card");
        tarjeta.setPadding(new Insets(12));
        return tarjeta;
    }
}
