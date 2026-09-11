package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Artista;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Pantalla de administración de artistas: tabla + formulario de alta/edición. */
public final class ArtistasPane extends BorderPane {

    private final CatalogClient client;
    private final String token;
    private final ObservableList<Artista> items = FXCollections.observableArrayList();
    private final TableView<Artista> table = new TableView<>(items);

    private final TextField nombreField = new TextField();
    private final TextField generoField = new TextField();
    private final TextField paisField = new TextField();
    private final Label status = new Label();
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private Artista seleccionado;

    public ArtistasPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setCenter(buildTable());
        setBottom(buildForm());
        cargar();
    }

    private TableView<Artista> buildTable() {
        TableColumn<Artista, String> nombre = new TableColumn<>("Nombre artístico");
        nombre.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreArtistico()));

        TableColumn<Artista, String> genero = new TableColumn<>("Género");
        genero.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().generoMusical()));

        TableColumn<Artista, String> pais = new TableColumn<>("País");
        pais.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().paisOrigen() == null ? "" : data.getValue().paisOrigen()));

        table.getColumns().addAll(List.of(nombre, genero, pais));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        nombreField.setPromptText("Nombre artístico");
        generoField.setPromptText("Género musical");
        paisField.setPromptText("País de origen (opcional)");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarSeleccion());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);

        HBox botones = new HBox(8, nuevoButton, guardarButton, eliminarButton);
        botones.setAlignment(Pos.CENTER_LEFT);

        status.setWrapText(true);

        VBox form = new VBox(8, nombreField, generoField, paisField, botones, status);
        form.setPadding(new Insets(12, 0, 0, 0));
        return form;
    }

    private void seleccionar(Artista artista) {
        seleccionado = artista;
        eliminarButton.setDisable(artista == null);
        if (artista == null) {
            return;
        }
        nombreField.setText(artista.nombreArtistico());
        generoField.setText(artista.generoMusical());
        paisField.setText(artista.paisOrigen() == null ? "" : artista.paisOrigen());
    }

    private void limpiarSeleccion() {
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        nombreField.clear();
        generoField.clear();
        paisField.clear();
        eliminarButton.setDisable(true);
        status.setText("");
    }

    private void cargar() {
        Async.run(
                () -> client.listArtistas(token),
                lista -> {
                    items.setAll(lista);
                    status.setText("");
                },
                status::setText);
    }

    private void guardar() {
        String nombre = nombreField.getText();
        String genero = generoField.getText();
        String pais = paisField.getText();
        if (nombre == null || nombre.isBlank() || genero == null || genero.isBlank()) {
            status.setText("Nombre artístico y género son obligatorios.");
            return;
        }
        String paisFinal = (pais == null || pais.isBlank()) ? null : pais.trim();

        guardarButton.setDisable(true);
        Runnable onDone = () -> {
            guardarButton.setDisable(false);
            limpiarSeleccion();
            cargar();
        };

        if (seleccionado == null) {
            Async.run(
                    () -> client.createArtista(token, nombre.trim(), genero.trim(), paisFinal),
                    ignored -> onDone.run(),
                    error -> {
                        guardarButton.setDisable(false);
                        status.setText(error);
                    });
        } else {
            Async.run(
                    () -> client.updateArtista(
                            token, seleccionado.idArtista(), nombre.trim(), genero.trim(), paisFinal),
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
        int id = seleccionado.idArtista();
        eliminarButton.setDisable(true);
        Async.run(
                () -> client.deleteArtista(token, id),
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
