package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Dashboard;
import gt.umg.svbgua.CatalogClient.Ocupacion;
import gt.umg.svbgua.CatalogClient.VentaPorArtista;
import gt.umg.svbgua.CatalogClient.VentaPorConcierto;
import gt.umg.svbgua.CatalogClient.VentaPorDia;
import gt.umg.svbgua.CatalogClient.VentaPorLocalidad;
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
import java.util.Set;
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
 * + una tabla con su gráfica por cada corte (concierto, artista, vendedor,
 * día y localidad) — los mismos que grafica el dashboard en vivo — más la tabla cruzada activa. Cada sección es una plantilla JasperReports chica e
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

    /**
     * @param pivote tabla cruzada que se ve en pantalla; null si todavía no cargó (esa sección se omite)
     * @param secciones las que se incluyen además de la portada, siempre presente; salen en el orden de
     *                  {@link SeccionPdf}, sin importar el orden en que se pidieron
     */
    public static void generar(Dashboard dashboard, Pivote pivote, Set<SeccionPdf> secciones, String generadoPor,
            String filtrosAplicados, File destino) throws JRException {
        List<JasperPrint> paginas = new ArrayList<>();
        paginas.add(llenarPortada(dashboard, generadoPor, filtrosAplicados));
        for (SeccionPdf seccion : SeccionPdf.values()) {
            if (!secciones.contains(seccion)) {
                continue;
            }
            switch (seccion) {
                case TABLA_CRUZADA -> {
                    if (pivote != null) {
                        paginas.add(llenarTablaCruzada(pivote));
                    }
                }
                case CONCIERTO -> paginas.add(llenarPorConcierto(dashboard));
                case ARTISTA -> paginas.add(llenarPorArtista(dashboard));
                case VENDEDOR -> paginas.add(llenarPorVendedor(dashboard));
                case DIA -> paginas.add(llenarPorDia(dashboard));
                case LOCALIDAD -> paginas.add(llenarPorLocalidad(dashboard));
            }
        }

        JRPdfExporter exportador = new JRPdfExporter();
        exportador.setExporterInput(SimpleExporterInput.getInstance(paginas));
        exportador.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exportador.exportReport();
    }

    private static JasperPrint llenarPortada(Dashboard dashboard, String generadoPor, String filtrosAplicados)
            throws JRException {
        String fecha = Fechas.fechaHora(LocalDateTime.now());

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("subtitulo", "Generado el " + fecha + " por " + generadoPor);
        parametros.put("filtros", filtrosAplicados);
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
            fila.put("ingresosNum", Double.valueOf(venta.ingresos()));
            filas.add(fila);
        }

        boolean hayDatos = dashboard.porConcierto().stream().anyMatch(v -> Double.parseDouble(v.ingresos()) > 0);
        JasperReport reporte = compilar("por_concierto.jrxml");
        return JasperFillManager.fillReport(reporte, parametrosConDatos(hayDatos), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorArtista(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorArtista venta : dashboard.porArtista()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("artista", venta.nombreArtistico());
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("boletos", String.valueOf(venta.boletos()));
            fila.put("ingresosNum", Double.valueOf(venta.ingresos()));
            filas.add(fila);
        }

        boolean hayDatos = dashboard.porArtista().stream().anyMatch(v -> Double.parseDouble(v.ingresos()) > 0);
        JasperReport reporte = compilar("por_artista.jrxml");
        return JasperFillManager.fillReport(reporte, parametrosConDatos(hayDatos), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorVendedor(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorVendedor venta : dashboard.porVendedor()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("vendedor", venta.nombreCompleto());
            fila.put("rol", venta.rol());
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("ventas", String.valueOf(venta.ventas()));
            fila.put("ingresosNum", Double.valueOf(venta.ingresos()));
            filas.add(fila);
        }

        boolean hayDatos = dashboard.porVendedor().stream().anyMatch(v -> Double.parseDouble(v.ingresos()) > 0);
        JasperReport reporte = compilar("por_vendedor.jrxml");
        return JasperFillManager.fillReport(reporte, parametrosConDatos(hayDatos), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorDia(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorDia venta : dashboard.porDia()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("dia", Fechas.dia(venta.dia()));
            // AAAA-MM-DD → DD/MM: la fecha completa no cabe en el eje X y JFreeChart la recorta a "...".
            fila.put("diaCorto", venta.dia().substring(8, 10) + "/" + venta.dia().substring(5, 7));
            fila.put("ingresos", formatearMoneda(venta.ingresos()));
            fila.put("boletos", String.valueOf(venta.boletos()));
            fila.put("ingresosNum", Double.valueOf(venta.ingresos()));
            filas.add(fila);
        }

        boolean hayDatos = dashboard.porDia().stream().anyMatch(v -> Double.parseDouble(v.ingresos()) > 0);
        JasperReport reporte = compilar("por_dia.jrxml");
        return JasperFillManager.fillReport(reporte, parametrosConDatos(hayDatos), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarPorLocalidad(Dashboard dashboard) throws JRException {
        List<Map<String, ?>> filas = new ArrayList<>();
        for (VentaPorLocalidad venta : dashboard.porLocalidad()) {
            Map<String, Object> fila = new HashMap<>();
            fila.put("localidad", venta.nombre());
            fila.put("boletos", String.valueOf(venta.boletos()));
            fila.put("boletosNum", venta.boletos());
            filas.add(fila);
        }

        boolean hayDatos = dashboard.porLocalidad().stream().anyMatch(v -> v.boletos() > 0);
        JasperReport reporte = compilar("por_localidad.jrxml");
        return JasperFillManager.fillReport(reporte, parametrosConDatos(hayDatos), new JRMapCollectionDataSource(filas));
    }

    private static JasperPrint llenarTablaCruzada(Pivote pivote) throws JRException {
        // El índice delante de cada etiqueta ("003|VIP") fija el orden en el crosstab, que ordena por el
        // texto del bucket: sin él, "20 de sep" quedaría antes que "5 de sep".
        List<String> filas = pivote.filas();
        List<String> columnas = pivote.columnas();
        List<Map<String, ?>> celdas = new ArrayList<>();
        for (int i = 0; i < filas.size(); i++) {
            for (int j = 0; j < columnas.size(); j++) {
                Double valor = pivote.valor(filas.get(i), columnas.get(j));
                if (valor == null) {
                    continue;
                }
                Map<String, Object> celda = new HashMap<>();
                celda.put("filaClave", String.format("%03d|%s", i, Pivote.presentar(pivote.dimensionFilas(), filas.get(i))));
                celda.put("columnaClave",
                        String.format("%03d|%s", j, Pivote.presentar(pivote.dimensionColumnas(), columnas.get(j))));
                celda.put("valorNum", valor);
                celdas.add(celda);
            }
        }

        Map<String, Object> parametros = new HashMap<>();
        parametros.put("titulo", pivote.titulo());
        parametros.put("esquina", Pivote.DIMENSIONES.get(pivote.dimensionFilas()) + " \\ "
                + Pivote.DIMENSIONES.get(pivote.dimensionColumnas()));
        parametros.put("esDinero", pivote.esDinero());
        parametros.put("maximo", pivote.maximo());
        return JasperFillManager.fillReport(compilar("tabla_cruzada.jrxml"), parametros,
                new JRMapCollectionDataSource(celdas));
    }

    // Mutable a propósito: JasperReports le hace put() internamente.
    private static Map<String, Object> parametrosConDatos(boolean hayDatos) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("hayDatos", hayDatos);
        return parametros;
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
