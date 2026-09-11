package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Localidad;
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

/** Pantalla de administración de localidades (VIP, Platea, General, ...). */
public final class LocalidadesPane extends BorderPane {

    private final CatalogClient client;
    private final String token;
    private final ObservableList<Localidad> items = FXCollections.observableArrayList();
    private final TableView<Localidad> table = new TableView<>(items);

    private final TextField nombreField = new TextField();
    private final Label status = new Label();
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private Localidad seleccionado;

    public LocalidadesPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setCenter(buildTable());
        setBottom(buildForm());
        cargar();
    }

    private TableView<Localidad> buildTable() {
        TableColumn<Localidad, String> nombre = new TableColumn<>("Nombre");
        nombre.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombre()));

        table.getColumns().add(nombre);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        nombreField.setPromptText("Nombre de la localidad");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarSeleccion());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);

        HBox botones = new HBox(8, nuevoButton, guardarButton, eliminarButton);
        botones.setAlignment(Pos.CENTER_LEFT);
        status.setWrapText(true);

        VBox form = new VBox(8, nombreField, botones, status);
        form.setPadding(new Insets(12, 0, 0, 0));
        return form;
    }

    private void seleccionar(Localidad localidad) {
        seleccionado = localidad;
        eliminarButton.setDisable(localidad == null);
        nombreField.setText(localidad == null ? "" : localidad.nombre());
    }

    private void limpiarSeleccion() {
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        nombreField.clear();
        eliminarButton.setDisable(true);
        status.setText("");
    }

    private void cargar() {
        Async.run(
                () -> client.listLocalidades(token),
                lista -> {
                    items.setAll(lista);
                    status.setText("");
                },
                status::setText);
    }

    private void guardar() {
        String nombre = nombreField.getText();
        if (nombre == null || nombre.isBlank()) {
            status.setText("El nombre es obligatorio.");
            return;
        }

        guardarButton.setDisable(true);
        Runnable onDone = () -> {
            guardarButton.setDisable(false);
            limpiarSeleccion();
            cargar();
        };
        Runnable onError = () -> guardarButton.setDisable(false);

        if (seleccionado == null) {
            Async.run(
                    () -> client.createLocalidad(token, nombre.trim()),
                    ignored -> onDone.run(),
                    error -> {
                        onError.run();
                        status.setText(error);
                    });
        } else {
            Async.run(
                    () -> client.updateLocalidad(token, seleccionado.idLocalidad(), nombre.trim()),
                    ignored -> onDone.run(),
                    error -> {
                        onError.run();
                        status.setText(error);
                    });
        }
    }

    private void eliminar() {
        if (seleccionado == null) {
            return;
        }
        int id = seleccionado.idLocalidad();
        eliminarButton.setDisable(true);
        Async.run(
                () -> client.deleteLocalidad(token, id),
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
