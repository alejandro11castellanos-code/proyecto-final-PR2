package gt.umg.svbgua;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cuentas del cobro en efectivo: interpretar el monto que escribe el vendedor y calcular el cambio.
 * Todo en {@link BigDecimal} con dos decimales: con {@code double} un total de Q 0.30 pagado con Q 0.30
 * podría dar "falta Q 0.00".
 */
final class CobroEfectivo {

    // "1,500" / "1,500.50": la coma separa miles.
    private static final Pattern CON_MILES = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?");
    // "150", "150.5", "150.50"
    private static final Pattern SIMPLE = Pattern.compile("\\d+(\\.\\d{1,2})?");
    // "150,5", "150,50": coma decimal (como se escribe en otros países).
    private static final Pattern COMA_DECIMAL = Pattern.compile("\\d+,\\d{1,2}");

    private CobroEfectivo() {
    }

    /**
     * Interpreta lo que escribió el vendedor ("Q 150", "1,500.50", "150,5"). Vacío si no es un monto válido:
     * texto, negativo, cero o más de dos decimales.
     */
    static Optional<BigDecimal> interpretar(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String limpio = texto.replace("Q", "").replace("q", "").replaceAll("\\s+", "");
        String normalizado;
        if (CON_MILES.matcher(limpio).matches()) {
            normalizado = limpio.replace(",", "");
        } else if (SIMPLE.matcher(limpio).matches()) {
            normalizado = limpio;
        } else if (COMA_DECIMAL.matcher(limpio).matches()) {
            normalizado = limpio.replace(',', '.');
        } else {
            return Optional.empty();
        }
        BigDecimal monto = new BigDecimal(normalizado).setScale(2, RoundingMode.UNNECESSARY);
        return monto.signum() > 0 ? Optional.of(monto) : Optional.empty();
    }

    /** El total del carrito (un double) llevado a quetzales exactos con dos decimales. */
    static BigDecimal aQuetzales(double total) {
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    static boolean alcanza(BigDecimal total, BigDecimal recibido) {
        return recibido.compareTo(total) >= 0;
    }

    /** Cambio a devolver; cero si el monto es justo. Solo tiene sentido si {@link #alcanza}. */
    static BigDecimal cambio(BigDecimal total, BigDecimal recibido) {
        return recibido.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Lo que falta para completar el pago; cero si ya alcanza. */
    static BigDecimal faltante(BigDecimal total, BigDecimal recibido) {
        return total.subtract(recibido).max(BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }
}
