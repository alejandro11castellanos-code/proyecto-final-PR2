package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Concierto;
import gt.umg.svbgua.CatalogClient.Inventario;
import gt.umg.svbgua.CatalogClient.ItemVenta;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Punto de venta: elegir concierto, ver disponibilidad en tiempo real por
 * localidad, armar un carrito con varios ítems (de uno o de varios conciertos) y
 * confirmar la venta en una sola transacción contra la API.
 */
public final class VentaPane extends BorderPane {

    private final CatalogClient client;
    private final String token;

    private final ComboBox<Concierto> conciertoCombo = new ComboBox<>();
    private final ObservableList<Inventario> disponibilidad = FXCollections.observableArrayList();
    private final TableView<Inventario> disponibilidadTable = new TableView<>(disponibilidad);
    private final Spinner<Integer> cantidadSpinner = new Spinner<>(1, 999, 1);
    private final Button agregarButton = new Button("Agregar al carrito");

    private final ObservableList<ItemCarrito> carrito = FXCollections.observableArrayList();
    private final TableView<ItemCarrito> carritoTable = new TableView<>(carrito);
    private final Label totalLabel = new Label("Total: Q 0.00");
    private final Button confirmarButton = new Button("Confirmar venta");
    private final Button vaciarButton = new Button("Vaciar carrito");
    private final ToggleButton tarjetaToggle = new ToggleButton("Tarjeta");
    private final ToggleButton efectivoToggle = new ToggleButton("Efectivo");
    private final Label status = new Label();
    private final PreviewPanel previewPanel = new PreviewPanel();
    private final FondoDifuminado fondo = new FondoDifuminado();

    public VentaPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setTop(buildSeleccionConcierto());
        setCenter(buildContenido());
        previewPanel.getStyleClass().add("card");
        previewPanel.setPadding(new Insets(14));
        previewPanel.managedProperty().bind(previewPanel.hayArtista());
        previewPanel.visibleProperty().bind(previewPanel.hayArtista());
        BorderPane.setMargin(previewPanel, new Insets(0, 0, 0, 16));
        previewPanel.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        // Ancho fijo: si cambiara con el nombre del artista, las tablas se estirarían y encogerían al elegir concierto.
        previewPanel.setMinWidth(168);
        previewPanel.setPrefWidth(168);
        previewPanel.setMaxWidth(168);
        BorderPane.setAlignment(previewPanel, Pos.CENTER);
        setRight(previewPanel);
        status.setWrapText(true);
        Tema.ocultarSiVacia(status);
        setBottom(status);

        // La portada del concierto elegido, desenfocada, como fondo de toda la pantalla.
        getChildren().add(0, fondo);
        previewPanel.imagenProperty().addListener((obs, anterior, imagen) -> fondo.mostrar(imagen));
        cargarConciertos();
    }

    @Override
    protected void layoutChildren() {
        fondo.resizeRelocate(0, 0, getWidth(), getHeight());
        super.layoutChildren();
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
        // Cambiar de concierto NO vacía el carrito: una misma compra puede mezclar boletos de varios.
        conciertoCombo.valueProperty().addListener((obs, previous, current) -> {
            status.setText("");
            if (current != null) {
                cargarDisponibilidad(current.idConcierto());
                previewPanel.cargarParaArtista(current.nombreArtistico());
            } else {
                disponibilidad.clear();
                previewPanel.limpiar();
            }
        });
        // Un concierto pudo crearse (o su aforo asignarse) en otra pestaña
        // después de armar esta pantalla.
        conciertoCombo.setOnShowing(event -> cargarConciertos());

        Label ayuda = new Label("Podés sumar boletos de varios conciertos en la misma compra: el carrito se conserva al cambiar.");
        ayuda.getStyleClass().add("muted");
        VBox box = new VBox(8, new Label("Concierto"), conciertoCombo, ayuda);
        box.setPadding(new Insets(0, 0, 12, 0));
        return box;
    }

    private HBox buildContenido() {
        HBox contenido = new HBox(16, buildDisponibilidadPane(), buildCarritoPane());
        HBox.setHgrow(contenido.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(contenido.getChildren().get(1), Priority.ALWAYS);
        return contenido;
    }

    private VBox buildDisponibilidadPane() {
        TableColumn<Inventario, String> localidad = new TableColumn<>("Localidad");
        localidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreLocalidad()));

        TableColumn<Inventario, String> precio = new TableColumn<>("Precio");
        precio.setCellValueFactory(data -> new SimpleStringProperty(formatearMoneda(data.getValue().precio())));

        TableColumn<Inventario, Inventario> disponible = ColumnaDisponibles.crear();

        disponibilidadTable.getColumns().addAll(List.<TableColumn<Inventario, ?>>of(localidad, precio, disponible));
        disponibilidadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        disponibilidadTable.getSelectionModel().selectedItemProperty()
                .addListener((obs, previous, current) -> agregarButton.setDisable(current == null));

        cantidadSpinner.setEditable(true);
        cantidadSpinner.setPrefWidth(90);
        agregarButton.setDisable(true);
        agregarButton.setOnAction(event -> agregarAlCarrito());

        Label cantidadLabel = new Label("Cantidad");
        cantidadLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        agregarButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        HBox controles = new HBox(8, cantidadLabel, cantidadSpinner, agregarButton);
        controles.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, new Label("Disponibilidad"), disponibilidadTable, controles);
        return box;
    }

    private VBox buildCarritoPane() {
        TableColumn<ItemCarrito, String> concierto = new TableColumn<>("Concierto");
        concierto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().tituloEvento()));

        TableColumn<ItemCarrito, String> localidad = new TableColumn<>("Localidad");
        localidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreLocalidad()));

        TableColumn<ItemCarrito, String> cantidad = new TableColumn<>("Cant.");
        cantidad.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().cantidad())));

        TableColumn<ItemCarrito, String> subtotal = new TableColumn<>("Subtotal");
        subtotal.setCellValueFactory(data -> new SimpleStringProperty(formatearMoneda(data.getValue().subtotal())));

        carritoTable.getColumns().addAll(List.of(concierto, localidad, cantidad, subtotal));
        carritoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button quitarButton = new Button("Quitar ítem");
        quitarButton.setOnAction(event -> quitarSeleccionDelCarrito());

        totalLabel.setStyle("-fx-font-weight: bold;");
        confirmarButton.setDisable(true);
        confirmarButton.setOnAction(event -> confirmarVenta());
        vaciarButton.setOnAction(event -> vaciarCarrito());

        // Un botón nunca se trunca ("Confirmar ve..."): si no caben en una línea, bajan a la siguiente.
        for (Button boton : List.of(quitarButton, vaciarButton, confirmarButton)) {
            boton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        }
        javafx.scene.layout.FlowPane botones = new javafx.scene.layout.FlowPane(8, 8, quitarButton, vaciarButton,
                confirmarButton);
        botones.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup metodoPago = new ToggleGroup();
        tarjetaToggle.setToggleGroup(metodoPago);
        efectivoToggle.setToggleGroup(metodoPago);
        tarjetaToggle.getStyleClass().add("segmento");
        efectivoToggle.getStyleClass().add("segmento");
        tarjetaToggle.setSelected(true);
        // Un ToggleGroup permite deseleccionar haciendo clic en el activo; siempre debe quedar un método elegido.
        metodoPago.selectedToggleProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) {
                anterior.setSelected(true);
            }
        });
        HBox pago = new HBox(8, new Label("Pago:"), tarjetaToggle, efectivoToggle);
        pago.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, new Label("Carrito"), carritoTable, totalLabel, pago, botones);
        return box;
    }

    private void cargarConciertos() {
        Async.run(
                () -> client.listConciertos(token),
                lista -> conciertoCombo.getItems().setAll(lista),
                status::setText);
    }

    void actualizar() {
        Concierto actual = conciertoCombo.getValue();
        if (actual != null) {
            cargarDisponibilidad(actual.idConcierto(), status.getText());
        }
    }

    private void cargarDisponibilidad(int idConcierto) {
        cargarDisponibilidad(idConcierto, "");
    }

    // `mensajeFinal` es lo que queda en el estado al terminar la recarga: así la confirmación
    // de una venta no se borra cuando llega la nueva disponibilidad.
    private void cargarDisponibilidad(int idConcierto, String mensajeFinal) {
        Async.run(
                () -> client.listInventario(token, idConcierto),
                lista -> {
                    disponibilidad.setAll(lista);
                    status.setText(mensajeFinal);
                },
                status::setText);
    }

    private void agregarAlCarrito() {
        Inventario seleccionado = disponibilidadTable.getSelectionModel().getSelectedItem();
        if (seleccionado == null) {
            return;
        }
        int cantidadPedida = cantidadSpinner.getValue();
        int yaEnCarrito = carrito.stream()
                .filter(item -> item.idInventario() == seleccionado.idInventario())
                .mapToInt(ItemCarrito::cantidad)
                .sum();

        if (yaEnCarrito + cantidadPedida > seleccionado.cantidadDisponible()) {
            status.setText("Solo hay " + seleccionado.cantidadDisponible() + " disponibles para "
                    + seleccionado.nombreLocalidad() + " (ya tenés " + yaEnCarrito + " en el carrito).");
            return;
        }

        double precioUnitario = Double.parseDouble(seleccionado.precio());
        String tituloEvento = conciertoCombo.getValue().tituloEvento();
        carrito.stream()
                .filter(item -> item.idInventario() == seleccionado.idInventario())
                .findFirst()
                .ifPresentOrElse(
                        existente -> {
                            int index = carrito.indexOf(existente);
                            carrito.set(index, new ItemCarrito(
                                    existente.idInventario(), existente.tituloEvento(), existente.nombreLocalidad(),
                                    existente.cantidad() + cantidadPedida, precioUnitario));
                        },
                        () -> carrito.add(new ItemCarrito(
                                seleccionado.idInventario(), tituloEvento, seleccionado.nombreLocalidad(),
                                cantidadPedida, precioUnitario)));

        status.setText("");
        actualizarTotal();
    }

    private void quitarSeleccionDelCarrito() {
        ItemCarrito seleccionado = carritoTable.getSelectionModel().getSelectedItem();
        if (seleccionado != null) {
            carrito.remove(seleccionado);
            actualizarTotal();
        }
    }

    private void vaciarCarrito() {
        carrito.clear();
        actualizarTotal();
        status.setText("");
    }

    private void actualizarTotal() {
        double total = carrito.stream().mapToDouble(ItemCarrito::subtotal).sum();
        totalLabel.setText("Total: " + formatearMoneda(total));
        confirmarButton.setDisable(carrito.isEmpty());
    }

    private void confirmarVenta() {
        if (carrito.isEmpty()) {
            return;
        }
        double total = carrito.stream().mapToDouble(ItemCarrito::subtotal).sum();
        if (efectivoToggle.isSelected()) {
            EfectivoDialog.mostrar(getScene().getWindow(), total, cambio -> registrarVenta("efectivo", cambio));
        } else {
            PagoDialog.mostrar(getScene().getWindow(), total, () -> registrarVenta("tarjeta", null));
        }
    }

    /** @param cambio a devolver al cliente si se pagó en efectivo; null con tarjeta */
    private void registrarVenta(String metodoPago, BigDecimal cambio) {
        List<ItemVenta> items = new ArrayList<>();
        for (ItemCarrito item : carrito) {
            items.add(new ItemVenta(item.idInventario(), item.cantidad()));
        }
        Concierto conciertoActual = conciertoCombo.getValue();

        confirmarButton.setDisable(true);
        Async.run(
                () -> client.crearVenta(token, items, metodoPago),
                venta -> {
                    String confirmacion = "Venta #" + venta.idVenta() + " registrada. Total: "
                            + formatearMoneda(venta.totalVenta()) + "."
                            + (cambio == null ? "" : " Cambio: " + formatearMoneda(cambio.doubleValue()) + ".");
                    vaciarCarrito();
                    status.setText(confirmacion);
                    if (conciertoActual != null) {
                        cargarDisponibilidad(conciertoActual.idConcierto(), confirmacion);
                    }
                    BoletosDialog.mostrar(getScene().getWindow(), client, token, venta.idVenta());
                },
                error -> {
                    confirmarButton.setDisable(false);
                    status.setText(error);
                    if (conciertoActual != null) {
                        // La disponibilidad pudo cambiar por otra venta concurrente; se
                        // refresca para que el vendedor vea el remanente real.
                        cargarDisponibilidad(conciertoActual.idConcierto());
                    }
                });
    }

    private static String formatearMoneda(String valor) {
        return formatearMoneda(Double.parseDouble(valor));
    }

    private static String formatearMoneda(double valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", valor);
    }

    private record ItemCarrito(int idInventario, String tituloEvento, String nombreLocalidad, int cantidad,
            double precioUnitario) {
        double subtotal() {
            return cantidad * precioUnitario;
        }
    }
}
