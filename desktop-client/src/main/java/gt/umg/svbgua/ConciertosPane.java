package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Artista;
import gt.umg.svbgua.CatalogClient.Concierto;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Pantalla de administración de conciertos. La fecha se ingresa en formato
 * ISO 8601 (por ejemplo {@code 2026-11-15T20:00:00Z}); una selección visual de
 * fecha/hora queda para una iteración posterior.
 */
public final class ConciertosPane extends BorderPane {

    private static final List<String> ESTADOS = List.of("programado", "activo", "finalizado", "cancelado");

    private final CatalogClient client;
    private final String token;
    private final ObservableList<Concierto> items = FXCollections.observableArrayList();
    private final ObservableList<Artista> artistas = FXCollections.observableArrayList();
    private final TableView<Concierto> table = new TableView<>(items);

    private final ComboBox<Artista> artistaCombo = new ComboBox<>(artistas);
    private final TextField tituloField = new TextField();
    private final TextField fechaField = new TextField();
    private final TextField recintoField = new TextField();
    private final ComboBox<String> estadoCombo = new ComboBox<>(FXCollections.observableArrayList(ESTADOS));
    private final Label status = new Label();
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private final Button escucharButton = new Button("▶ Escuchar");
    private final PreviewClient previewClient = PreviewClient.crear();
    private final PreviewPlayer previewPlayer = new PreviewPlayer();
    private Concierto seleccionado;

    public ConciertosPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setCenter(buildTable());
        setBottom(buildForm());
        cargarArtistas();
        cargar();
    }

    private TableView<Concierto> buildTable() {
        TableColumn<Concierto, String> titulo = new TableColumn<>("Evento");
        titulo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().tituloEvento()));

        TableColumn<Concierto, String> artista = new TableColumn<>("Artista");
        artista.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreArtistico()));

        TableColumn<Concierto, String> fecha = new TableColumn<>("Fecha");
        fecha.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fechaConcierto()));

        TableColumn<Concierto, String> recinto = new TableColumn<>("Recinto");
        recinto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().recinto()));

        TableColumn<Concierto, String> estado = new TableColumn<>("Estado");
        estado.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().estado()));

        table.getColumns().addAll(List.of(titulo, artista, fecha, recinto, estado));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        artistaCombo.setPromptText("Artista");
        artistaCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Artista artista) {
                return artista == null ? "" : artista.nombreArtistico();
            }

            @Override
            public Artista fromString(String text) {
                return artistaCombo.getValue();
            }
        });
        tituloField.setPromptText("Título del evento");
        fechaField.setPromptText("Fecha (ISO 8601, ej. 2026-11-15T20:00:00Z)");
        recintoField.setPromptText("Recinto");
        estadoCombo.setPromptText("Estado");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarSeleccion());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);
        escucharButton.setOnAction(event -> alternarReproduccion());
        escucharButton.setDisable(true);

        HBox botones = new HBox(8, nuevoButton, guardarButton, eliminarButton, escucharButton);
        botones.setAlignment(Pos.CENTER_LEFT);
        status.setWrapText(true);

        VBox form = new VBox(8, artistaCombo, tituloField, fechaField, recintoField, estadoCombo, botones, status);
        form.setPadding(new Insets(12, 0, 0, 0));
        return form;
    }

    private void seleccionar(Concierto concierto) {
        detenerReproduccion();
        seleccionado = concierto;
        eliminarButton.setDisable(concierto == null);
        escucharButton.setDisable(concierto == null);
        if (concierto == null) {
            return;
        }
        artistas.stream()
                .filter(a -> a.idArtista() == concierto.idArtista())
                .findFirst()
                .ifPresent(artistaCombo::setValue);
        tituloField.setText(concierto.tituloEvento());
        fechaField.setText(concierto.fechaConcierto());
        recintoField.setText(concierto.recinto());
        estadoCombo.setValue(concierto.estado());
    }

    private void limpiarSeleccion() {
        detenerReproduccion();
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        artistaCombo.setValue(null);
        tituloField.clear();
        fechaField.clear();
        recintoField.clear();
        estadoCombo.setValue(null);
        eliminarButton.setDisable(true);
        escucharButton.setDisable(true);
        status.setText("");
    }

    private void detenerReproduccion() {
        previewPlayer.detener();
        escucharButton.setText("▶ Escuchar");
    }

    private void alternarReproduccion() {
        if (previewPlayer.estaReproduciendo()) {
            detenerReproduccion();
            status.setText("");
            return;
        }
        if (seleccionado == null) {
            return;
        }

        String nombreArtista = seleccionado.nombreArtistico();
        escucharButton.setDisable(true);
        status.setText("Buscando un adelanto de " + nombreArtista + "...");

        Async.run(
                () -> previewClient.buscarPreview(nombreArtista),
                url -> {
                    escucharButton.setDisable(false);
                    if (url == null) {
                        status.setText("No se encontró un adelanto de " + nombreArtista + ".");
                        return;
                    }
                    status.setText("Reproduciendo un adelanto de " + nombreArtista + "...");
                    escucharButton.setText("■ Detener");
                    previewPlayer.reproducir(url, () -> {
                        escucharButton.setText("▶ Escuchar");
                        status.setText("");
                    });
                },
                error -> {
                    escucharButton.setDisable(false);
                    status.setText(error);
                });
    }

    private void cargarArtistas() {
        Async.run(
                () -> client.listArtistas(token),
                lista -> artistas.setAll(lista),
                status::setText);
    }

    private void cargar() {
        Async.run(
                () -> client.listConciertos(token),
                lista -> {
                    items.setAll(lista);
                    status.setText("");
                },
                status::setText);
    }

    private void guardar() {
        Artista artista = artistaCombo.getValue();
        String titulo = tituloField.getText();
        String fecha = fechaField.getText();
        String recinto = recintoField.getText();
        String estado = estadoCombo.getValue();

        if (artista == null || titulo == null || titulo.isBlank()
                || fecha == null || fecha.isBlank() || recinto == null || recinto.isBlank()) {
            status.setText("Artista, título, fecha y recinto son obligatorios.");
            return;
        }
        if (seleccionado != null && estado == null) {
            status.setText("Al editar un concierto, el estado es obligatorio.");
            return;
        }

        guardarButton.setDisable(true);
        Runnable onDone = () -> {
            guardarButton.setDisable(false);
            limpiarSeleccion();
            cargar();
        };

        if (seleccionado == null) {
            Async.run(
                    () -> client.createConcierto(
                            token, artista.idArtista(), titulo.trim(), fecha.trim(), recinto.trim(), estado),
                    ignored -> onDone.run(),
                    error -> {
                        guardarButton.setDisable(false);
                        status.setText(error);
                    });
        } else {
            Async.run(
                    () -> client.updateConcierto(token, seleccionado.idConcierto(), artista.idArtista(),
                            titulo.trim(), fecha.trim(), recinto.trim(), estado),
                    ignored -> onDone.run(),
                    error -> {
                        guardarButton.setDisable(false);
                        status.setText(error);
                    });
        }
    }

    private void eliminar() {
        if (seleccionado == null) {
            return;
        }
        int id = seleccionado.idConcierto();
        eliminarButton.setDisable(true);
        Async.run(
                () -> client.deleteConcierto(token, id),
                () -> {
                    limpiarSeleccion();
                    cargar();
                },
                error -> {
                    eliminarButton.setDisable(false);
                    status.setText(error);
                });
    }
}
