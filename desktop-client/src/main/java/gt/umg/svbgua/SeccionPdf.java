package gt.umg.svbgua;

/**
 * Secciones del reporte PDF que el usuario puede incluir u omitir. El orden de las constantes es el orden
 * de las páginas en el documento (la portada con los KPIs y los filtros va siempre primero y no es opcional).
 */
public enum SeccionPdf {
    TABLA_CRUZADA("Tabla cruzada"),
    CONCIERTO("Ventas por concierto"),
    ARTISTA("Ventas por artista"),
    VENDEDOR("Ranking de vendedores"),
    DIA("Ventas por día"),
    LOCALIDAD("Boletos por localidad");

    private final String titulo;

    SeccionPdf(String titulo) {
        this.titulo = titulo;
    }

    public String titulo() {
        return titulo;
    }
}
