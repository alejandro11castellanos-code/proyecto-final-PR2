package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Dashboard;
import gt.umg.svbgua.CatalogClient.Ocupacion;
import gt.umg.svbgua.CatalogClient.VentaPorArtista;
import gt.umg.svbgua.CatalogClient.VentaPorConcierto;
import gt.umg.svbgua.CatalogClient.VentaPorDia;
import gt.umg.svbgua.CatalogClient.VentaPorLocalidad;
import gt.umg.svbgua.CatalogClient.VentaPorVendedor;
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
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Dashboard de reportería en vivo: gráficas nativas de JavaFX consumiendo
 * GET /reportes/dashboard. No usa JasperReports — ese queda para un reporte
 * exportable/imprimible aparte; acá el objetivo es una pantalla que se
 * navega mientras se usa la app, no un documento.
 */
public final class ReportesPane extends BorderPane {

    private final CatalogClient client;
    private final String token;

    private final Label kpiIngresos = new Label();
    private final Label kpiBoletos = new Label();
    private final Label kpiPromedio = new Label();
    private final Label status = new Label();
    private final VBox contenido = new VBox(20);

    public ReportesPane(CatalogClient client, String token) {
        this.client = client;
        this.token = token;
        setPadding(new Insets(16));
        setTop(buildEncabezado());

        contenido.setPadding(new Insets(16, 0, 0, 0));
        ScrollPane scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        setCenter(scroll);

        cargar();
    }

    private VBox buildEncabezado() {
        Button actualizarButton = new Button("Actualizar");
        actualizarButton.setOnAction(event -> cargar());

        HBox kpis = new HBox(24, tarjetaKpi("Ingresos totales", kpiIngresos),
                tarjetaKpi("Boletos vendidos", kpiBoletos), tarjetaKpi("Precio promedio", kpiPromedio));
        kpis.setAlignment(Pos.CENTER_LEFT);

        HBox fila = new HBox(16, kpis, actualizarButton);
        fila.setAlignment(Pos.CENTER_LEFT);

        status.setWrapText(true);
        VBox top = new VBox(8, fila, status);
        return top;
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
        Async.run(
                () -> client.obtenerDashboard(token),
                this::mostrar,
                error -> status.setText(error));
    }

    private void mostrar(Dashboard dashboard) {
        status.setText("");
        kpiIngresos.setText(formatearMoneda(dashboard.resumen().ingresosTotales()));
        kpiBoletos.setText(String.valueOf(dashboard.resumen().boletosVendidos()));
        kpiPromedio.setText(formatearMoneda(dashboard.resumen().precioPromedio()));

        contenido.getChildren().setAll(
                seccion("Ventas por concierto", barChartIngresos(dashboard.porConcierto())),
                seccion("Ventas por artista", barChartArtista(dashboard.porArtista())),
                seccion("Ranking de vendedores", barChartVendedor(dashboard.porVendedor())),
                seccion("Ventas por día", lineChartPorDia(dashboard.porDia())),
                seccion("Boletos por localidad", pieChartLocalidad(dashboard.porLocalidad())),
                seccion("Ocupación por concierto", ocupacionPane(dashboard.ocupacion())));
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
            serie.getData().add(new XYChart.Data<>(fila.tituloEvento(), Double.parseDouble(fila.ingresos())));
        }
        chart.getData().add(serie);
        return chart;
    }

    private BarChart<String, Number> barChartArtista(List<VentaPorArtista> filas) {
        BarChart<String, Number> chart = nuevoBarChart("Ingresos (Q)");
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorArtista fila : filas) {
            serie.getData().add(new XYChart.Data<>(fila.nombreArtistico(), Double.parseDouble(fila.ingresos())));
        }
        chart.getData().add(serie);
        return chart;
    }

    private BarChart<String, Number> barChartVendedor(List<VentaPorVendedor> filas) {
        BarChart<String, Number> chart = nuevoBarChart("Ingresos (Q)");
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        for (VentaPorVendedor fila : filas) {
            String etiqueta = fila.nombreCompleto() + ("administrador".equals(fila.rol()) ? " (admin)" : "");
            serie.getData().add(new XYChart.Data<>(etiqueta, Double.parseDouble(fila.ingresos())));
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
            serie.getData().add(new XYChart.Data<>(fila.dia(), Double.parseDouble(fila.ingresos())));
        }
        chart.getData().add(serie);
        return chart;
    }

    private PieChart pieChartLocalidad(List<VentaPorLocalidad> filas) {
        PieChart chart = new PieChart(FXCollections.observableArrayList(
                filas.stream()
                        .filter(fila -> fila.boletos() > 0)
                        .map(fila -> new PieChart.Data(fila.nombre(), fila.boletos()))
                        .toList()));
        chart.setPrefHeight(280);
        return chart;
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
        CategoryAxis ejeX = new CategoryAxis();
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel(etiquetaEjeY);
        BarChart<String, Number> chart = new BarChart<>(ejeX, ejeY);
        chart.setLegendVisible(false);
        chart.setPrefHeight(260);
        HBox.setHgrow(chart, Priority.ALWAYS);
        return chart;
    }

    private static String formatearMoneda(String valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", Double.parseDouble(valor));
    }
}
