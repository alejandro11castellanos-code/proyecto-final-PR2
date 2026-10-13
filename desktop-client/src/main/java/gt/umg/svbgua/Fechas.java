package gt.umg.svbgua;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/**
 * Formato de fechas para el usuario final. La API entrega y recibe fecha y hora
 * locales de Guatemala, sin zona ("2026-11-15T20:00:00"), así que aquí no se
 * convierte ninguna zona horaria: se muestra la hora tal como se guardó.
 */
final class Fechas {

    private static final Locale ES = Locale.of("es", "GT");
    private static final DateTimeFormatter PARA_API = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter LEGIBLE_FECHA = DateTimeFormatter.ofPattern("d MMM yyyy", ES);
    private static final DateTimeFormatter LEGIBLE_FECHA_HORA = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ES);
    static final DateTimeFormatter ENTRADA_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Fechas() {
    }

    static Optional<LocalDateTime> parsear(String iso) {
        if (iso == null || iso.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(iso.trim()));
        } catch (DateTimeParseException error) {
            return Optional.empty();
        }
    }

    static String paraApi(LocalDateTime fechaHora) {
        return fechaHora.format(PARA_API);
    }

    /** "15 nov 2026, 20:00"; si el texto no se puede interpretar se muestra tal cual en vez de ocultarlo. */
    static String fechaHora(String iso) {
        return parsear(iso).map(Fechas::fechaHora).orElse(iso == null ? "" : iso);
    }

    static String fechaHora(LocalDateTime fechaHora) {
        return fechaHora.format(LEGIBLE_FECHA_HORA);
    }

    static String fecha(LocalDate fecha) {
        return fecha.format(LEGIBLE_FECHA);
    }

    /** Para un día suelto "2026-09-10" (reportes). */
    static String dia(String iso) {
        try {
            return fecha(LocalDate.parse(iso));
        } catch (DateTimeParseException | NullPointerException error) {
            return iso == null ? "" : iso;
        }
    }
}
