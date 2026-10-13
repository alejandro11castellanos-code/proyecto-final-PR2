package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Artista;
import gt.umg.svbgua.CatalogClient.Concierto;
import gt.umg.svbgua.CatalogClient.Dashboard;
import gt.umg.svbgua.CatalogClient.Localidad;
import gt.umg.svbgua.CatalogClient.Ocupacion;
import gt.umg.svbgua.CatalogClient.VentaPorArtista;
import gt.umg.svbgua.CatalogClient.VentaPorConcierto;
import gt.umg.svbgua.CatalogClient.VentaPorDia;
import gt.umg.svbgua.CatalogClient.VentaPorLocalidad;
import gt.umg.svbgua.CatalogClient.Usuario;
import gt.umg.svbgua.CatalogClient.VentaPorVendedor;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import net.sf.jasperreports.engine.JRException;

/**
 * Dashboard de reportería en vivo: gráficas nativas de JavaFX consumiendo
 * GET /reportes/dashboard. No usa JasperReports — ese queda para un reporte
 * exportable/imprimible aparte; acá el objetivo es una pantalla que se
 * navega mientras se usa la app, no un documento.
 */
public final class ReportesPane extends BorderPane {

    private static final double ANCHO_BARRA_ANGOSTA = 430;
    // Casi el ancho de dos angostas + el espacio entre ellas (430*2 + 16).
    private static final double ANCHO_BARRA_ANCHA = 876;

    private final CatalogClient client;
    private final String token;
    private final String nombreCompleto;

    private final Label kpiIngresos = new Label();
    private final Label kpiBoletos = new Label();
    private final Label kpiPromedio = new Label();
    private final Label status = new Label();
    private final VBox contenido = new VBox(20);
    private final Button exportarButton = new Button("Exportar PDF");
    private Dashboard ultimoDashboard;
    private String ultimaDescripcionFiltros = FiltrosReporte.NINGUNO.descripcion(null, null, null, null);

    private final DatePicker desdePicker = new DatePicker();
    private final DatePicker hastaPicker = new DatePicker();
    private final ComboBox<OpcionFiltro<Integer>> artistaCombo = new ComboBox<>();
    private final ComboBox<OpcionFiltro<Integer>> conciertoCombo = new ComboBox<>();
    private final ComboBox<OpcionFiltro<Integer>> vendedorCombo = new ComboBox<>();
    private final ComboBox<OpcionFiltro<Integer>> localidadCombo = new ComboBox<>();
    private final Label resumenFiltros = new Label();
    private List<Concierto> conciertos = List.of();
    private FiltrosReporte filtrosAplicados = FiltrosReporte.NINGUNO;

    public ReportesPane(CatalogClient client, String token, String nombreCompleto) {
        this.client = client;
        this.token = token;
        this.nombreCompleto = nombreCompleto;
        setPadding(new Insets(16));
        setTop(buildEncabezado());

        contenido.setPadding(new Insets(16, 0, 0, 0));
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        setCenter(scroll);

        cargarCatalogos();
        cargar();
    }

    private VBox buildEncabezado() {
        Button actualizarButton = new Button("Actualizar");
        actualizarButton.setOnAction(event -> cargar());

        exportarButton.setDisable(true);
        exportarButton.setOnAction(event -> exportarPdf());

        HBox kpis = new HBox(24, tarjetaKpi("Ingresos totales", kpiIngresos),
                tarjetaKpi("Boletos vendidos", kpiBoletos), tarjetaKpi("Precio promedio", kpiPromedio));
        kpis.setAlignment(Pos.CENTER_LEFT);

        HBox fila = new HBox(16, kpis, actualizarButton, exportarButton);
        fila.setAlignment(Pos.CENTER_LEFT);

        resumenFiltros.setStyle("-fx-font-size: 11px; -fx-opacity: 0.8;");
        resumenFiltros.setWrapText(true);
        status.setWrapText(true);
        VBox top = new VBox(8, fila, buildFiltros(), resumenFiltros, status);
        return top;
    }

    private FlowPane buildFiltros() {
        desdePicker.setPromptText("Desde");
        hastaPicker.setPromptText("Hasta");
        desdePicker.setPrefWidth(130);
        hastaPicker.setPrefWidth(130);

        inicializarCombo(artistaCombo, "Todos los artistas");
        inicializarCombo(conciertoCombo, "Todos los conciertos");
        inicializarCombo(vendedorCombo, "Todos los vendedores");
        inicializarCombo(localidadCombo, "Todas las localidades");
        // Al elegir artista, el combo de conciertos solo ofrece los suyos:
        // así no se puede armar la combinación (artista A + concierto de B).
        artistaCombo.valueProperty().addListener((obs, previo, actual) -> {
            if (actual != null) {
                actualizarConciertos();
            }
        });

        Button aplicarButton = new Button("Aplicar filtros");
        aplicarButton.setOnAction(event -> aplicarFiltros());
        Button limpiarButton = new Button("Limpiar");
        limpiarButton.setOnAction(event -> limpiarFiltros());

        FlowPane filtros = new FlowPane(8, 8, new Label("Fecha de compra:"), desdePicker, hastaPicker,
                artistaCombo, conciertoCombo, vendedorCombo, localidadCombo, aplicarButton, limpiarButton);
        filtros.setAlignment(Pos.CENTER_LEFT);
        return filtros;
    }

    private static void inicializarCombo(ComboBox<OpcionFiltro<Integer>> combo, String etiquetaTodos) {
        combo.setPrefWidth(190);
        combo.getItems().setAll(new OpcionFiltro<>(etiquetaTodos, null));
        combo.getSelectionModel().selectFirst();
    }

    private void cargarCatalogos() {
        Async.run(() -> client.listArtistas(token),
                lista -> llenarCombo(artistaCombo, "Todos los artistas", lista, Artista::nombreArtistico,
                        Artista::idArtista),
                error -> status.setText(error));
        Async.run(() -> client.listConciertos(token),
                lista -> {
                    conciertos = lista;
                    actualizarConciertos();
                },
                error -> status.setText(error));
        // Todos los usuarios, no solo los 'vendedor': un administrador también puede vender
        // y sus ventas cuentan en el ranking.
        Async.run(() -> client.listUsuarios(token),
                lista -> llenarCombo(vendedorCombo, "Todos los vendedores", lista,
                        usuario -> usuario.nombreCompleto() + ("administrador".equals(usuario.rol()) ? " (admin)" : ""),
                        Usuario::idUsuario),
                error -> status.setText(error));
        Async.run(() -> client.listLocalidades(token),
                lista -> llenarCombo(localidadCombo, "Todas las localidades", lista, Localidad::nombre,
                        Localidad::idLocalidad),
                error -> status.setText(error));
    }

    private static <T> void llenarCombo(ComboBox<OpcionFiltro<Integer>> combo, String etiquetaTodos, List<T> datos,
            java.util.function.Function<T, String> etiqueta, java.util.function.ToIntFunction<T> id) {
        List<OpcionFiltro<Integer>> opciones = new ArrayList<>();
        opciones.add(new OpcionFiltro<>(etiquetaTodos, null));
        for (T dato : datos) {
            opciones.add(new OpcionFiltro<>(etiqueta.apply(dato), id.applyAsInt(dato)));
        }
        combo.getItems().setAll(opciones);
        combo.getSelectionModel().selectFirst();
    }

    private void actualizarConciertos() {
        Integer idArtista = valor(artistaCombo);
        List<OpcionFiltro<Integer>> opciones = new ArrayList<>();
        opciones.add(new OpcionFiltro<>("Todos los conciertos", null));
        for (Concierto concierto : conciertos) {
            if (idArtista == null || concierto.idArtista() == idArtista) {
                opciones.add(new OpcionFiltro<>(concierto.tituloEvento(), concierto.idConcierto()));
            }
        }
        conciertoCombo.getItems().setAll(opciones);
        conciertoCombo.getSelectionModel().selectFirst();
    }

    private static Integer valor(ComboBox<OpcionFiltro<Integer>> combo) {
        OpcionFiltro<Integer> opcion = combo.getValue();
        return opcion == null ? null : opcion.valor();
    }

    private static String etiqueta(ComboBox<OpcionFiltro<Integer>> combo) {
        OpcionFiltro<Integer> opcion = combo.getValue();
        return opcion == null ? null : opcion.etiqueta();
    }

    private void aplicarFiltros() {
        LocalDate desde = desdePicker.getValue();
        LocalDate hasta = hastaPicker.getValue();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            status.setText("La fecha \"Desde\" no puede ser posterior a \"Hasta\".");
            return;
        }
        filtrosAplicados = new FiltrosReporte(desde, hasta, valor(artistaCombo), valor(conciertoCombo),
                valor(vendedorCombo), valor(localidadCombo));
        cargar();
    }

    private void limpiarFiltros() {
        desdePicker.setValue(null);
        hastaPicker.setValue(null);
        artistaCombo.getSelectionModel().selectFirst();
        actualizarConciertos();
        vendedorCombo.getSelectionModel().selectFirst();
        localidadCombo.getSelectionModel().selectFirst();
        filtrosAplicados = FiltrosReporte.NINGUNO;
        cargar();
    }

    private String describir(FiltrosReporte filtros) {
        return filtros.descripcion(etiqueta(artistaCombo), etiqueta(conciertoCombo), etiqueta(vendedorCombo),
                etiqueta(localidadCombo));
    }

    private VBox tarjetaKpi(String titulo, Label valor) {
        Label tituloLabel = new Label(titulo);
        tituloLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        valor.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        VBox tarjeta = new VBox(2, tituloLabel, valor);
        tarjeta.setPadding(new Insets(8, 16, 8, 16));
        tarjeta.setStyle("-fx-border-color: derive(-fx-base, -20%); -fx-border-radius: 4; -fx-border-width: 1;");
        return tarjeta;
    }

    private void cargar() {
        status.setText("Cargando...");
        // La descripción se fija junto con la petición: si el usuario cambia los combos
        // mientras carga, el PDF debe describir los filtros del dashboard que se ve.
        FiltrosReporte filtros = filtrosAplicados;
        String descripcion = describir(filtros);
        Async.run(
                () -> client.obtenerDashboard(token, filtros),
                dashboard -> mostrar(dashboard, descripcion, filtros.hayFiltros()),
                error -> status.setText(error));
    }

    private void exportarPdf() {
        if (ultimoDashboard == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar reporte de ventas");
        chooser.setInitialFileName("reporte-ventas-" + LocalDate.now() + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        Window ventana = getScene() == null ? null : getScene().getWindow();
        File destino = chooser.showSaveDialog(ventana);
        if (destino == null) {
            return;
        }

        Dashboard dashboard = ultimoDashboard;
        String descripcion = ultimaDescripcionFiltros;
        exportarButton.setDisable(true);
        status.setText("Generando PDF...");
        Async.run(
                () -> {
                    try {
                        ReporteVentasService.generar(dashboard, nombreCompleto, descripcion, destino);
                        return destino;
                    } catch (JRException error) {
                        throw new RuntimeException(error.getMessage(), error);
                    }
                },
                archivo -> {
                    exportarButton.setDisable(false);
                    status.setText("PDF guardado en " + archivo.getAbsolutePath());
                },
                error -> {
                    exportarButton.setDisable(false);
                    status.setText(error);
                });
    }

    private void mostrar(Dashboard dashboard, String descripcionFiltros, boolean conFiltros) {
        status.setText("");
        ultimoDashboard = dashboard;
        ultimaDescripcionFiltros = descripcionFiltros;
        resumenFiltros.setText(conFiltros ? "Filtros aplicados: " + descripcionFiltros : "");
        exportarButton.setDisable(false);
        kpiIngresos.setText(formatearMoneda(dashboard.resumen().ingresosTotales()));
        kpiBoletos.setText(String.valueOf(dashboard.resumen().boletosVendidos()));
        kpiPromedio.setText(formatearMoneda(dashboard.resumen().precioPromedio()));

        // Las barras quedan mucho mas angostas (y legibles) de a dos por fila
        // en vez de una sola gráfica ocupando todo el ancho disponible.
        FlowPane barras = new FlowPane(16, 20);
        barras.getChildren().addAll(
                seccion("Ventas por concierto", barChartIngresos(dashboard.porConcierto())),
                seccion("Ranking de vendedores", barChartVendedor(dashboard.porVendedor())),
                // Va tercera y sola en su fila, con casi el ancho de las otras dos juntas.
                seccion("Ventas por artista", barChartArtista(dashboard.porArtista(), ANCHO_BARRA_ANCHA)));

        contenido.getChildren().setAll(
                barras,
                seccion("Ventas por día", lineChartPorDia(dashboard.porDia())),
                seccion("Boletos por localidad", pieChartLocalidad(dashboard.porLocalidad())),
                seccion("Ocupación por concierto (inventario actual: no depende de fechas ni vendedor)", ocupacionPane(dashboard.ocupacion())));
    }

    private VBox seccion(String titulo, javafx.scene.Node grafico) {
        Label label = new Label(titulo);
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        VBox box = new VBox(8, label, grafico);
        return box;
    }

    private BarChart<String, Number> barChartIngresos(List<VentaPorConcierto> filas) {
        BarChart<String, Number> chart = nuevoBarChart("Ingresos (Q)");
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorConcierto fila : filas) {
            XYChart.Data<String, Number> punto = new XYChart.Data<>(fila.tituloEvento(), Double.parseDouble(fila.ingresos()));
            instalarTooltip(punto, fila.tituloEvento() + "\n" + formatearMoneda(fila.ingresos())
                    + " · " + fila.boletos() + " boletos");
            serie.getData().add(punto);
        }
        chart.getData().add(serie);
        return chart;
    }

    private BarChart<String, Number> barChartArtista(List<VentaPorArtista> filas, double ancho) {
        BarChart<String, Number> chart = nuevoBarChart("Ingresos (Q)", ancho);
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorArtista fila : filas) {
            XYChart.Data<String, Number> punto =
                    new XYChart.Data<>(fila.nombreArtistico(), Double.parseDouble(fila.ingresos()));
            instalarTooltip(punto, fila.nombreArtistico() + "\n" + formatearMoneda(fila.ingresos())
                    + " · " + fila.boletos() + " boletos");
            serie.getData().add(punto);
        }
        chart.getData().add(serie);
        return chart;
    }

    private BarChart<String, Number> barChartVendedor(List<VentaPorVendedor> filas) {
        BarChart<String, Number> chart = nuevoBarChart("Ingresos (Q)");
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorVendedor fila : filas) {
            String etiqueta = fila.nombreCompleto() + ("administrador".equals(fila.rol()) ? " (admin)" : "");
            XYChart.Data<String, Number> punto = new XYChart.Data<>(etiqueta, Double.parseDouble(fila.ingresos()));
            instalarTooltip(punto, etiqueta + "\n" + formatearMoneda(fila.ingresos())
                    + " · " + fila.ventas() + " ventas");
            serie.getData().add(punto);
        }
        chart.getData().add(serie);
        return chart;
    }

    private LineChart<String, Number> lineChartPorDia(List<VentaPorDia> filas) {
        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel("Ingresos (Q)");
        LineChart<String, Number> chart = new LineChart<>(ejeX, ejeY);
        chart.setLegendVisible(false);
        chart.setPrefHeight(260);

        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorDia fila : filas) {
            XYChart.Data<String, Number> punto = new XYChart.Data<>(fila.dia(), Double.parseDouble(fila.ingresos()));
            instalarTooltip(punto, fila.dia() + "\n" + formatearMoneda(fila.ingresos())
                    + " · " + fila.boletos() + " boletos");
            serie.getData().add(punto);
        }
        chart.getData().add(serie);
        return chart;
    }

    private PieChart pieChartLocalidad(List<VentaPorLocalidad> filas) {
        List<PieChart.Data> datos = filas.stream()
                .filter(fila -> fila.boletos() > 0)
                .map(fila -> new PieChart.Data(fila.nombre(), fila.boletos()))
                .toList();
        PieChart chart = new PieChart(FXCollections.observableArrayList(datos));
        chart.setPrefHeight(280);

        for (PieChart.Data dato : datos) {
            instalarTooltip(dato, dato.getName() + "\n" + (int) dato.getPieValue() + " boletos");
        }
        return chart;
    }

    /** Instala un tooltip en el nodo de un punto/barra, esperando a que exista si todavía no se creó. */
    private static void instalarTooltip(XYChart.Data<?, ?> punto, String texto) {
        Tooltip tooltip = new Tooltip(texto);
        tooltip.setShowDelay(Duration.millis(100));
        if (punto.getNode() != null) {
            Tooltip.install(punto.getNode(), tooltip);
        }
        punto.nodeProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo != null) {
                Tooltip.install(nuevo, tooltip);
            }
        });
    }

    /** Igual que arriba, pero para porciones de {@link PieChart}. */
    private static void instalarTooltip(PieChart.Data porcion, String texto) {
        Tooltip tooltip = new Tooltip(texto);
        tooltip.setShowDelay(Duration.millis(100));
        if (porcion.getNode() != null) {
            Tooltip.install(porcion.getNode(), tooltip);
        }
        porcion.nodeProperty().addListener((obs, anterior, nuevo) -> {
            if (nuevo != null) {
                Tooltip.install(nuevo, tooltip);
            }
        });
    }

    private FlowPane ocupacionPane(List<Ocupacion> filas) {
        FlowPane pane = new FlowPane(16, 16);
        for (Ocupacion fila : filas) {
            pane.getChildren().add(tarjetaOcupacion(fila));
        }
        return pane;
    }

    private VBox tarjetaOcupacion(Ocupacion fila) {
        double porcentaje = fila.cantidadTotal() > 0 ? (double) fila.vendido() / fila.cantidadTotal() : 0;

        Label titulo = new Label(fila.tituloEvento());
        titulo.setWrapText(true);
        titulo.setStyle("-fx-font-weight: bold;");

        ProgressBar barra = new ProgressBar(porcentaje);
        barra.setPrefWidth(180);

        Label detalle = new Label(fila.vendido() + " / " + fila.cantidadTotal()
                + String.format(Locale.of("es", "GT"), " (%.0f%%)", porcentaje * 100));
        detalle.setStyle("-fx-font-size: 11px;");

        VBox tarjeta = new VBox(6, titulo, barra, detalle);
        tarjeta.setPadding(new Insets(8));
        tarjeta.setPrefWidth(220);
        tarjeta.setStyle("-fx-border-color: derive(-fx-base, -20%); -fx-border-radius: 4; -fx-border-width: 1;");
        return tarjeta;
    }

    private BarChart<String, Number> nuevoBarChart(String etiquetaEjeY) {
        return nuevoBarChart(etiquetaEjeY, ANCHO_BARRA_ANGOSTA);
    }

    private BarChart<String, Number> nuevoBarChart(String etiquetaEjeY, double ancho) {
        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel(etiquetaEjeY);
        BarChart<String, Number> chart = new BarChart<>(ejeX, ejeY);
        chart.setLegendVisible(false);
        chart.setPrefSize(ancho, 260);
        chart.setMaxWidth(ancho);
        chart.setCategoryGap(20);
        return chart;
    }

    private static String formatearMoneda(String valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", Double.parseDouble(valor));
    }
}
