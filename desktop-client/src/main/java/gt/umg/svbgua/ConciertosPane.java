package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Artista;
import gt.umg.svbgua.CatalogClient.Concierto;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
import javafx.scene.layout.Priority;
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
    private final FilteredList<Concierto> filtrado = new FilteredList<>(items, concierto -> true);
    private final ObservableList<Artista> artistas = FXCollections.observableArrayList();
    private final TableView<Concierto> table = new TableView<>(items);
    private final TextField buscarField = new TextField();

    private final ComboBox<Artista> artistaCombo = new ComboBox<>(artistas);
    private final TextField tituloField = new TextField();
    private final TextField fechaField = new TextField();
    private final TextField recintoField = new TextField();
    private final ComboBox<String> estadoCombo = new ComboBox<>(FXCollections.observableArrayList(ESTADOS));
    private final Label status = new Label();
    private final Label tituloForm = new Label("Nuevo concierto");
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private final PreviewPanel previewPanel = new PreviewPanel();
    private Concierto seleccionado;

    public ConciertosPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        // La vista previa va debajo del formulario (no a un lado) para no quitarle ancho a la tabla.
        previewPanel.getStyleClass().add("card");
        previewPanel.setPadding(new Insets(14));
        previewPanel.setPrefWidth(340);
        previewPanel.setMaxWidth(340);
        previewPanel.managedProperty().bind(previewPanel.hayArtista());
        previewPanel.visibleProperty().bind(previewPanel.hayArtista());
        setCenter(Formulario.lado(buildTablaConBuscador(), new VBox(14, buildForm(), previewPanel)));
        cargarArtistas();
        cargar();
    }

    private VBox buildTablaConBuscador() {
        buscarField.setPromptText("Buscar por evento, artista, recinto o estado...");
        buscarField.textProperty().addListener((obs, previo, actual) -> aplicarFiltro(actual));

        TableView<Concierto> tabla = buildTable();
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox box = new VBox(8, buscarField, tabla);
        return box;
    }

    private void aplicarFiltro(String texto) {
        String buscado = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        filtrado.setPredicate(concierto -> buscado.isEmpty()
                || concierto.tituloEvento().toLowerCase(Locale.ROOT).contains(buscado)
                || concierto.nombreArtistico().toLowerCase(Locale.ROOT).contains(buscado)
                || concierto.recinto().toLowerCase(Locale.ROOT).contains(buscado)
                || concierto.estado().toLowerCase(Locale.ROOT).contains(buscado));
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
        table.setItems(filtrado);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        artistaCombo.setPromptText("Seleccione");
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
        // Un artista pudo darse de alta en otra pestaña después de armar esta.
        artistaCombo.setOnShowing(event -> cargarArtistas());
        tituloField.setPromptText("Ej. Gira Aniversario 2026");
        fechaField.setPromptText("2026-11-15T20:00:00Z");
        recintoField.setPromptText("Ej. Teatro Nacional");
        estadoCombo.setPromptText("Seleccione");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarSeleccion());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);

        status.setWrapText(true);
        nuevoButton.getStyleClass().add("secondary");
        eliminarButton.getStyleClass().add("danger");
        status.setWrapText(true);
        Tema.ocultarSiVacia(status);

        return Formulario.tarjeta(tituloForm, "Seleccione un concierto de la tabla o cree uno nuevo.",
                Formulario.campo("Artista", artistaCombo),
                Formulario.campo("Título del evento", tituloField),
                Formulario.campo("Fecha y hora (ISO 8601)", fechaField),
                Formulario.dosColumnas(Formulario.campo("Recinto", recintoField), Formulario.campo("Estado", estadoCombo)),
                Formulario.botones(guardarButton, nuevoButton, eliminarButton),
                status);
    }

    private void seleccionar(Concierto concierto) {
        seleccionado = concierto;
        tituloForm.setText(concierto == null ? "Nuevo concierto" : "Editar concierto");
        eliminarButton.setDisable(concierto == null);
        if (concierto == null) {
            previewPanel.limpiar();
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
        previewPanel.cargarParaArtista(concierto.nombreArtistico());
    }

    private void limpiarSeleccion() {
        previewPanel.limpiar();
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        tituloForm.setText("Nuevo concierto");
        artistaCombo.setValue(null);
        tituloField.clear();
        fechaField.clear();
        recintoField.clear();
        estadoCombo.setValue(null);
        eliminarButton.setDisable(true);
        status.setText("");
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
