package gt.umg.svbgua;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Filtros opcionales del dashboard de reportes; null significa "sin filtrar por eso". */
public record FiltrosReporte(LocalDate desde, LocalDate hasta, Integer idArtista, Integer idConcierto,
        Integer idVendedor, Integer idLocalidad) {

    public static final FiltrosReporte NINGUNO = new FiltrosReporte(null, null, null, null, null, null);

    public boolean hayFiltros() {
        return !equals(NINGUNO);
    }

    /** "?desde=...&artista=..." o cadena vacía; los nombres coinciden con los de GET /reportes/dashboard. */
    public String queryString() {
        List<String> partes = new ArrayList<>();
        agregar(partes, "desde", desde);
        agregar(partes, "hasta", hasta);
        agregar(partes, "artista", idArtista);
        agregar(partes, "concierto", idConcierto);
        agregar(partes, "vendedor", idVendedor);
        agregar(partes, "localidad", idLocalidad);
        return partes.isEmpty() ? "" : "?" + String.join("&", partes);
    }

    /** Texto para la portada del PDF; los nombres solo se usan si el filtro correspondiente está activo. */
    public String descripcion(String artista, String concierto, String vendedor, String localidad) {
        List<String> partes = new ArrayList<>();
        if (desde != null && hasta != null) {
            partes.add("Fechas: " + Fechas.fecha(desde) + " al " + Fechas.fecha(hasta));
        } else if (desde != null) {
            partes.add("Desde el " + Fechas.fecha(desde));
        } else if (hasta != null) {
            partes.add("Hasta el " + Fechas.fecha(hasta));
        }
        if (idArtista != null) {
            partes.add("Artista: " + artista);
        }
        if (idConcierto != null) {
            partes.add("Concierto: " + concierto);
        }
        if (idVendedor != null) {
            partes.add("Vendedor: " + vendedor);
        }
        if (idLocalidad != null) {
            partes.add("Localidad: " + localidad);
        }
        return partes.isEmpty() ? "Sin filtros (todas las ventas)" : String.join(" · ", partes);
    }

    private static void agregar(List<String> partes, String nombre, Object valor) {
        if (valor != null) {
            partes.add(nombre + "=" + valor);
        }
    }
}
