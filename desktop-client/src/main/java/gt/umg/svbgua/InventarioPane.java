package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Concierto;
import gt.umg.svbgua.CatalogClient.Inventario;
import gt.umg.svbgua.CatalogClient.Localidad;
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
 * Pantalla de administración de aforo: elegir un concierto y asignarle
 * precio + cupo por localidad. Sin esto, el punto de venta no tiene nada que
 * mostrar para ese concierto.
 */
public final class InventarioPane extends BorderPane {

    private final CatalogClient client;
    private final String token;

    private final ComboBox<Concierto> conciertoCombo = new ComboBox<>();
    private final ObservableList<Inventario> items = FXCollections.observableArrayList();
    private final TableView<Inventario> table = new TableView<>(items);

    private final ObservableList<Localidad> localidades = FXCollections.observableArrayList();
    private final ComboBox<Localidad> localidadCombo = new ComboBox<>(localidades);
    private final TextField precioField = new TextField();
    private final TextField cantidadField = new TextField();
    private final Button guardarButton = new Button("Guardar");
    private final Button eliminarButton = new Button("Eliminar");
    private final Label status = new Label();
    private final Label tituloForm = new Label("Nuevo cupo");
    private Inventario seleccionado;

    public InventarioPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setTop(buildSeleccionConcierto());
        setCenter(Formulario.lado(buildTable(), buildForm()));
        cargarConciertos();
        cargarLocalidades();
    }

    private VBox buildSeleccionConcierto() {
        conciertoCombo.setPromptText("Elegí un concierto");
        conciertoCombo.setPrefWidth(360);
        conciertoCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Concierto concierto) {
                return concierto == null ? "" : concierto.tituloEvento() + " — " + concierto.nombreArtistico();
            }

            @Override
            public Concierto fromString(String text) {
                return conciertoCombo.getValue();
            }
        });
        conciertoCombo.valueProperty().addListener((obs, previous, current) -> {
            limpiarFormulario();
            if (current != null) {
                cargarInventario(current.idConcierto());
            } else {
                items.clear();
            }
        });
        // Otra pestaña pudo crear un concierto nuevo después de que esta se
        // armó; se refresca justo antes de desplegar, no solo al abrir la vista.
        conciertoCombo.setOnShowing(event -> cargarConciertos());

        VBox box = new VBox(8, new Label("Concierto"), conciertoCombo);
        box.setPadding(new Insets(0, 0, 12, 0));
        return box;
    }

    private TableView<Inventario> buildTable() {
        TableColumn<Inventario, String> localidad = new TableColumn<>("Localidad");
        localidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreLocalidad()));

        TableColumn<Inventario, String> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().precio()));

        TableColumn<Inventario, String> total = new TableColumn<>("Cupo total");
        total.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().cantidadTotal())));

        TableColumn<Inventario, Inventario> disponible = ColumnaDisponibles.crear();

        // El witness explícito evita "unchecked generic array creation": sin él,
        // el compilador intenta inferir un tipo común entre columnas de T
        // distinto (String vs. Inventario) pasándolas por varargs.
        table.getColumns().addAll(List.<TableColumn<Inventario, ?>>of(localidad, precio, total, disponible));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previous, current) -> seleccionar(current));
        return table;
    }

    private VBox buildForm() {
        localidadCombo.setPromptText("Seleccione");
        localidadCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Localidad localidad) {
                return localidad == null ? "" : localidad.nombre();
            }

            @Override
            public Localidad fromString(String text) {
                return localidadCombo.getValue();
            }
        });
        localidadCombo.setOnShowing(event -> cargarLocalidades());
        precioField.setPromptText("450.00");
        cantidadField.setPromptText("Ej. 300");

        Button nuevoButton = new Button("Nuevo");
        nuevoButton.setOnAction(event -> limpiarFormulario());
        guardarButton.setOnAction(event -> guardar());
        eliminarButton.setOnAction(event -> eliminar());
        eliminarButton.setDisable(true);

        status.setWrapText(true);
        nuevoButton.getStyleClass().add("secondary");
        eliminarButton.getStyleClass().add("danger");
        status.setWrapText(true);
        Tema.ocultarSiVacia(status);

        return Formulario.tarjeta(tituloForm, "Elija un concierto, luego seleccione una localidad de la tabla o cree un cupo nuevo.",
                Formulario.campo("Localidad", localidadCombo),
                Formulario.dosColumnas(Formulario.campo("Precio (Q)", precioField), Formulario.campo("Cupo total", cantidadField)),
                Formulario.botones(guardarButton, nuevoButton, eliminarButton),
                status);
    }

    private void seleccionar(Inventario inventario) {
        seleccionado = inventario;
        tituloForm.setText(inventario == null ? "Nuevo cupo" : "Editar cupo");
        eliminarButton.setDisable(inventario == null);
        if (inventario == null) {
            return;
        }
        localidades.stream()
                .filter(l -> l.idLocalidad() == inventario.idLocalidad())
                .findFirst()
                .ifPresent(localidadCombo::setValue);
        precioField.setText(inventario.precio());
        cantidadField.setText(String.valueOf(inventario.cantidadTotal()));
    }

    private void limpiarFormulario() {
        table.getSelectionModel().clearSelection();
        seleccionado = null;
        tituloForm.setText("Nuevo cupo");
        localidadCombo.setValue(null);
        precioField.clear();
        cantidadField.clear();
        eliminarButton.setDisable(true);
        status.setText("");
    }

    private void cargarConciertos() {
        Async.run(
                () -> client.listConciertos(token),
                lista -> conciertoCombo.getItems().setAll(lista),
                status::setText);
    }

    private void cargarLocalidades() {
        Async.run(
                () -> client.listLocalidades(token),
                lista -> localidades.setAll(lista),
                status::setText);
    }

    void actualizar() {
        Concierto actual = conciertoCombo.getValue();
        if (actual != null) {
            cargarInventario(actual.idConcierto());
        }
    }

    private void cargarInventario(int idConcierto) {
        Async.run(
                () -> client.listInventario(token, idConcierto),
                lista -> {
                    items.setAll(lista);
                    status.setText("");
                },
                status::setText);
    }

    private void guardar() {
        Concierto concierto = conciertoCombo.getValue();
        Localidad localidad = localidadCombo.getValue();
        if (concierto == null) {
            status.setText("Elegí un concierto primero.");
            return;
        }
        if (localidad == null) {
            status.setText("Elegí una localidad.");
            return;
        }

        double precio;
        int cantidadTotal;
        try {
            precio = Double.parseDouble(precioField.getText().trim());
            cantidadTotal = Integer.parseInt(cantidadField.getText().trim());
        } catch (NumberFormatException | NullPointerException error) {
            status.setText("Precio y cupo total deben ser números válidos.");
            return;
        }

        int idConcierto = concierto.idConcierto();
        guardarButton.setDisable(true);
        Runnable onDone = () -> {
            guardarButton.setDisable(false);
            limpiarFormulario();
            cargarInventario(idConcierto);
        };

        if (seleccionado == null) {
            Async.run(
                    () -> client.createInventario(token, idConcierto, localidad.idLocalidad(), precio, cantidadTotal),
                    ignored -> onDone.run(),
                    error -> {
                        guardarButton.setDisable(false);
                        status.setText(error);
                    });
        } else {
            Async.run(
                    () -> client.updateInventario(token, idConcierto, seleccionado.idInventario(), precio, cantidadTotal),
                    ignored -> onDone.run(),
                    error -> {
                        guardarButton.setDisable(false);
                        status.setText(error);
                    });
        }
    }

    private void eliminar() {
        Concierto concierto = conciertoCombo.getValue();
        if (seleccionado == null || concierto == null) {
            return;
        }
        int idConcierto = concierto.idConcierto();
        int idInventario = seleccionado.idInventario();
        eliminarButton.setDisable(true);
        Async.run(
                () -> client.deleteInventario(token, idConcierto, idInventario),
                () -> {
                    limpiarFormulario();
                    cargarInventario(idConcierto);
                },
                error -> {
                    eliminarButton.setDisable(false);
                    status.setText(error);
                });
    }
}
