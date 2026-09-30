package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Concierto;
import gt.umg.svbgua.CatalogClient.Usuario;
import gt.umg.svbgua.CatalogClient.VentaResumen;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Historial de ventas — un vendedor ve solo las suyas (el backend lo fuerza
 * por token, esta pantalla ni le ofrece el filtro de vendedor); un
 * administrador puede filtrar por vendedor y/o concierto. Reabre el mismo
 * {@link BoletosDialog} de la fase 5 para reimprimir o reenviar los boletos
 * de una venta pasada — no hace falta pantalla nueva para eso.
 */
public final class HistorialPane extends BorderPane {

    private final CatalogClient client;
    private final String token;
    private final boolean esAdmin;

    private final ObservableList<VentaResumen> items = FXCollections.observableArrayList();
    private final TableView<VentaResumen> table = new TableView<>(items);

    private final ComboBox<Usuario> vendedorCombo = new ComboBox<>();
    private final ComboBox<Concierto> conciertoCombo = new ComboBox<>();
    private final Button verBoletosButton = new Button("Ver boletos");
    private final Label status = new Label();
    private VentaResumen seleccionada;

    public HistorialPane(CatalogClient client, String token, boolean esAdmin) {
        this.client = client;
        this.token = token;
        this.esAdmin = esAdmin;
        setPadding(new Insets(16));
        setTop(buildFiltros());
        setCenter(buildTable());
        setBottom(buildAcciones());

        if (esAdmin) {
            cargarVendedores();
        }
        cargarConciertos();
        cargar();
    }

    private HBox buildFiltros() {
        conciertoCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Concierto concierto) {
                return concierto == null ? "Todos los conciertos" : concierto.tituloEvento();
            }

            @Override
            public Concierto fromString(String text) {
                return conciertoCombo.getValue();
            }
        });
        conciertoCombo.valueProperty().addListener((obs, previo, actual) -> cargar());

        HBox filtros = new HBox(8, conciertoCombo);
        filtros.setAlignment(Pos.CENTER_LEFT);
        filtros.setPadding(new Insets(0, 0, 12, 0));

        if (esAdmin) {
            vendedorCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Usuario usuario) {
                    return usuario == null ? "Todos los vendedores" : usuario.nombreCompleto();
                }

                @Override
                public Usuario fromString(String text) {
                    return vendedorCombo.getValue();
                }
            });
            vendedorCombo.valueProperty().addListener((obs, previo, actual) -> cargar());
            filtros.getChildren().add(0, vendedorCombo);
        }

        return filtros;
    }

    private TableView<VentaResumen> buildTable() {
        TableColumn<VentaResumen, String> fecha = new TableColumn<>("Fecha");
        fecha.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fechaVenta()));

        TableColumn<VentaResumen, String> id = new TableColumn<>("Venta #");
        id.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().idVenta())));

        TableColumn<VentaResumen, String> vendedor = new TableColumn<>("Vendedor");
        vendedor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().nombreVendedor()));

        TableColumn<VentaResumen, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(data -> new SimpleStringProperty(formatearMoneda(data.getValue().totalVenta())));

        TableColumn<VentaResumen, String> boletos = new TableColumn<>("Boletos");
        boletos.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().boletos())));

        table.getColumns().addAll(List.of(fecha, id, vendedor, total, boletos));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getSelectionModel().selectedItemProperty().addListener((obs, previo, actual) -> {
            seleccionada = actual;
            verBoletosButton.setDisable(actual == null);
        });
        return table;
    }

    private HBox buildAcciones() {
        verBoletosButton.setDisable(true);
        verBoletosButton.setOnAction(event -> {
            if (seleccionada != null) {
                BoletosDialog.mostrar(getScene().getWindow(), client, token, seleccionada.idVenta());
            }
        });

        Button actualizarButton = new Button("Actualizar");
        actualizarButton.setOnAction(event -> cargar());

        status.setWrapText(true);

        VBox pie = new VBox(8, new HBox(8, verBoletosButton, actualizarButton), status);
        pie.setPadding(new Insets(12, 0, 0, 0));
        HBox contenedor = new HBox(pie);
        return contenedor;
    }

    private void cargarVendedores() {
        Async.run(
                () -> client.listUsuarios(token),
                lista -> {
                    List<Usuario> conTodos = new ArrayList<>();
                    conTodos.add(null);
                    lista.stream().filter(u -> "vendedor".equals(u.rol())).forEach(conTodos::add);
                    vendedorCombo.getItems().setAll(conTodos);
                    vendedorCombo.setValue(null);
                },
                status::setText);
    }

    private void cargarConciertos() {
        Async.run(
                () -> client.listConciertos(token),
                lista -> {
                    List<Concierto> conTodos = new ArrayList<>();
                    conTodos.add(null);
                    conTodos.addAll(lista);
                    conciertoCombo.getItems().setAll(conTodos);
                    conciertoCombo.setValue(null);
                },
                status::setText);
    }

    private void cargar() {
        status.setText("Cargando...");
        Integer idVendedor = esAdmin && vendedorCombo.getValue() != null ? vendedorCombo.getValue().idUsuario() : null;
        Integer idConcierto = conciertoCombo.getValue() != null ? conciertoCombo.getValue().idConcierto() : null;

        Async.run(
                () -> client.listarVentas(token, idVendedor, idConcierto),
                lista -> {
                    items.setAll(lista);
                    status.setText(lista.isEmpty() ? "No hay ventas para este filtro." : "");
                },
                status::setText);
    }

    private static String formatearMoneda(String valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", Double.parseDouble(valor));
    }
}
