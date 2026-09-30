package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Dashboard;
import gt.umg.svbgua.CatalogClient.Ocupacion;
import gt.umg.svbgua.CatalogClient.VentaPorArtista;
import gt.umg.svbgua.CatalogClient.VentaPorConcierto;
import gt.umg.svbgua.CatalogClient.VentaPorVendedor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

/**
 * Genera el reporte de ventas en PDF con JasperReports: portada con los KPIs
 * + tres tablas (por concierto, por artista, por vendedor) — los mismos tres
 * cortes que pide el enunciado original y que el dashboard en vivo ya
 * grafica. Cada sección es una plantilla JasperReports chica e
 * independiente; se combinan al exportar en un solo PDF (vía
 * {@link JRPdfExporter}), en vez de usar subreports — más simple de
 * mantener a mano sin un diseñador visual.
 *
 * <p>Nota de implementación: las filas de cada tabla viajan como
 * {@code Map<String, ?>} ({@link JRMapCollectionDataSource}), no como los
 * {@code record} de {@link CatalogClient} directamente — JasperReports lee
 * datasources de bean por convención JavaBean ({@code getX()}), y los
 * records de Java exponen sus campos como {@code x()}, no {@code getX()}.
 */
public final class ReporteVentasService {

    private ReporteVentasService() {
    }

    public static void generar(Dashboard dashboard, String generadoPor, File destino) throws JRException {
        List<JasperPrint> secciones = List.of(
                llenarPortada(dashboard, generadoPor),
                llenarPorConcierto(dashboard),
                llenarPorArtista(dashboard),
                llenarPorVendedor(dashboard));

        JRPdfExporter exportador = new JRPdfExporter();
        exportador.setExporterInput(SimpleExporterInput.getInstance(secciones));
        exportador.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exportador.exportReport();
    }

    private static JasperPrint llenarPortada(Dashboard dashboard, String generadoPor) throws JRException {
        String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("subtitulo", "Generado el " + fecha + " por " + generadoPor);
        parametros.put("ingresosTotales", formatearMoneda(dashboard.resumen().ingresosTotales()));
        parametros.put("boletosVendidos", String.valueOf(dashboard.resumen().boletosVendidos()));
        parametros.put("precioPromedio", formatearMoneda(dashboard.resumen().precioPromedio()));

        return JasperFillManager.fillReport(compilar("portada.jrxml"), parametros, new JREmptyDataSource());
    }

    private static JasperPrint llenarPorConcierto(Dashboard dashboard) throws JRException {
        Map<Integer, Ocupacion> ocupacionPorConcierto = new HashMap<>();
        for (Ocupacion ocupacion : dashboard.ocupacion()) {
            ocupacionPorConcierto.put(ocupacion.idConcierto(), ocupacion);
        }

        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorConcierto venta : dashboard.porConcierto()) {
            Ocupacion ocupacion = ocupacionPorConcierto.get(venta.idConcierto());
            String porcentaje = (ocupacion == null || ocupacion.cantidadTotal() == 0)
                    ? "—"
                    : String.format(Locale.of("es", "GT"), "%.0f%%",
                            100.0 * ocupacion.vendido() / ocupacion.cantidadTotal());

            Map<String, Object> fila = new HashMap<>();
            fila.put("concierto", venta.tituloEvento());
            fila.put("artista", venta.nombreArtistico());
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("boletos", String.valueOf(venta.boletos()));
            fila.put("ocupacion", porcentaje);
            filas.add(fila);
        }

        JasperReport reporte = compilar("por_concierto.jrxml");
        return JasperFillManager.fillReport(reporte, new HashMap<>(), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorArtista(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorArtista venta : dashboard.porArtista()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("artista", venta.nombreArtistico());
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("boletos", String.valueOf(venta.boletos()));
            filas.add(fila);
        }

        JasperReport reporte = compilar("por_artista.jrxml");
        return JasperFillManager.fillReport(reporte, new HashMap<>(), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorVendedor(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorVendedor venta : dashboard.porVendedor()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("vendedor", venta.nombreCompleto());
            fila.put("rol", venta.rol());
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("ventas", String.valueOf(venta.ventas()));
            filas.add(fila);
        }

        JasperReport reporte = compilar("por_vendedor.jrxml");
        return JasperFillManager.fillReport(reporte, new HashMap<>(), new JRMapCollectionDataSource(filas));
    }

    private static JasperReport compilar(String recurso) throws JRException {
        try (InputStream entrada = ReporteVentasService.class.getResourceAsStream("reportes/" + recurso)) {
            if (entrada == null) {
                throw new JRException("No se encontró la plantilla 'reportes/" + recurso + "' en el classpath.");
            }
            return JasperCompileManager.compileReport(entrada);
        } catch (IOException error) {
            throw new JRException(error);
        }
    }

    private static String formatearMoneda(String valor) {
        return String.format(Locale.of("es", "GT"), "Q %,.2f", Double.parseDouble(valor));
    }
}
