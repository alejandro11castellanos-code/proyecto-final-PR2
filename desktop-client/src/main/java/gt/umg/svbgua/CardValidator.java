package gt.umg.svbgua;

import java.time.YearMonth;

/**
 * Validación local de un número de tarjeta simulado: algoritmo de Luhn,
 * vencimiento y CVV. No se conecta a ningún banco ni pasarela — el
 * enunciado excluye explícitamente integrar una pasarela de pago real, así
 * que esto se queda 100% local, sin red, solo para que el punto de venta
 * "cobre" de forma creíble en la demo.
 */
public final class CardValidator {

    private CardValidator() {
    }

    /** Algoritmo de Luhn: valida el checksum de un número de tarjeta (13 a 19 dígitos). */
    public static boolean luhnValido(String numero) {
        String limpio = soloDigitos(numero);
        if (limpio.length() < 13 || limpio.length() > 19) {
            return false;
        }

        int suma = 0;
        boolean duplicar = false;
        for (int i = limpio.length() - 1; i >= 0; i--) {
            int digito = limpio.charAt(i) - '0';
            if (duplicar) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    /** Marca de la tarjeta según el prefijo del número (solo para mostrarla en pantalla). */
    public static String marca(String numero) {
        String limpio = soloDigitos(numero);
        if (limpio.isEmpty()) {
            return "";
        }
        if (limpio.startsWith("4")) {
            return "Visa";
        }
        if (limpio.matches("^5[1-5]\\d*$") || limpio.matches("^2(2[2-9]\\d|2[3-9]\\d{2}|[3-6]\\d{3}|7[01]\\d{2}|720\\d)\\d*$")) {
            return "Mastercard";
        }
        if (limpio.matches("^3[47]\\d*$")) {
            return "American Express";
        }
        return "";
    }

    /** Acepta "MM/AA" o "MM/AAAA"; rechaza formato inválido o un mes ya vencido. */
    public static boolean vencimientoValido(String mmAa) {
        if (mmAa == null) {
            return false;
        }
        String[] partes = mmAa.trim().split("/");
        if (partes.length != 2) {
            return false;
        }
        try {
            int mes = Integer.parseInt(partes[0].trim());
            int anio = Integer.parseInt(partes[1].trim());
            if (anio < 100) {
                anio += 2000;
            }
            if (mes < 1 || mes > 12) {
                return false;
            }
            return !YearMonth.of(anio, mes).isBefore(YearMonth.now());
        } catch (NumberFormatException error) {
            return false;
        }
    }

    /** CVV de 3 o 4 dígitos (Amex usa 4, el resto 3). */
    public static boolean cvvValido(String cvv) {
        return cvv != null && cvv.matches("\\d{3,4}");
    }

    private static String soloDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("[^0-9]", "");
    }
}
