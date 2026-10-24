package gt.umg.svbgua;

import gt.umg.svbgua.CatalogClient.Celda;
import gt.umg.svbgua.CatalogClient.TablaCruzada;
import java.text.Collator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Matriz de una tabla cruzada lista para mostrar o exportar: filas y columnas
 * ordenadas, el valor de cada celda y los totales. Las combinaciones sin
 * ventas no existen en la respuesta del servidor; acá simplemente no tienen
 * valor ({@link #valor} devuelve null) y no suman a los totales.
 */
final class Pivote {

    /** Dimensiones que acepta GET /reportes/cruzado, con su nombre para pantalla (orden de los selectores). */
    static final Map<String, String> DIMENSIONES = Map.of(
            "concierto", "Concierto",
            "localidad", "Localidad",
            "artista", "Artista",
            "vendedor", "Vendedor",
            "dia", "Día",
            "metodo", "Método de pago");
    static final List<String> ORDEN_DIMENSIONES =
            List.of("concierto", "localidad", "artista", "vendedor", "dia", "metodo");

    static final Map<String, String> MEDIDAS = Map.of("boletos", "Boletos", "ingresos", "Ingresos (Q)");
    static final List<String> ORDEN_MEDIDAS = List.of("boletos", "ingresos");

    private final String dimensionFilas;
    private final String dimensionColumnas;
    private final String medida;
    private final List<String> filas;
    private final List<String> columnas;
    private final Map<String, Map<String, Double>> valores = new HashMap<>();
    private final Map<String, Double> totalPorFila = new HashMap<>();
    private final Map<String, Double> totalPorColumna = new HashMap<>();
    private double granTotal;
    private double maximo;

    private Pivote(TablaCruzada tabla) {
        this.dimensionFilas = tabla.filas();
        this.dimensionColumnas = tabla.columnas();
        this.medida = tabla.medida();

        Collator orden = Collator.getInstance(Locale.of("es", "GT"));
        TreeSet<String> conjuntoFilas = new TreeSet<>(orden);
        TreeSet<String> conjuntoColumnas = new TreeSet<>(orden);
        for (Celda celda : tabla.celdas()) {
            double valor = Double.parseDouble(celda.valor());
            conjuntoFilas.add(celda.fila());
            conjuntoColumnas.add(celda.columna());
            valores.computeIfAbsent(celda.fila(), clave -> new HashMap<>()).put(celda.columna(), valor);
            totalPorFila.merge(celda.fila(), valor, Double::sum);
            totalPorColumna.merge(celda.columna(), valor, Double::sum);
            granTotal += valor;
            maximo = Math.max(maximo, valor);
        }
        this.filas = List.copyOf(conjuntoFilas);
        this.columnas = List.copyOf(conjuntoColumnas);
    }

    static Pivote de(TablaCruzada tabla) {
        return new Pivote(tabla);
    }

    String dimensionFilas() {
        return dimensionFilas;
    }

    String dimensionColumnas() {
        return dimensionColumnas;
    }

    String medida() {
        return medida;
    }

    boolean esDinero() {
        return "ingresos".equals(medida);
    }

    List<String> filas() {
        return filas;
    }

    List<String> columnas() {
        return columnas;
    }

    boolean vacia() {
        return filas.isEmpty();
    }

    /** Valor de la celda, o null si esa combinación no tuvo ventas. */
    Double valor(String fila, String columna) {
        Map<String, Double> deLaFila = valores.get(fila);
        return deLaFila == null ? null : deLaFila.get(columna);
    }

    double totalFila(String fila) {
        return totalPorFila.getOrDefault(fila, 0.0);
    }

    double totalColumna(String columna) {
        return totalPorColumna.getOrDefault(columna, 0.0);
    }

    double granTotal() {
        return granTotal;
    }

    /** Valor de celda más alto: escala del mapa de calor. */
    double maximo() {
        return maximo;
    }

    /** Título legible, p. ej. "Boletos por Concierto × Localidad". */
    String titulo() {
        return MEDIDAS.get(medida).replace(" (Q)", "") + " por " + DIMENSIONES.get(dimensionFilas) + " × "
                + DIMENSIONES.get(dimensionColumnas);
    }

    /** Texto de un encabezado: los días llegan como AAAA-MM-DD y se muestran en formato legible. */
    static String presentar(String dimension, String clave) {
        return "dia".equals(dimension) ? Fechas.dia(clave) : clave;
    }

    /** Valor formateado: dinero con "Q" y miles, boletos como entero. */
    String formatear(double valor) {
        return esDinero()
                ? String.format(Locale.of("es", "GT"), "Q %,.2f", valor)
                : String.format(Locale.of("es", "GT"), "%,d", Math.round(valor));
    }
}
