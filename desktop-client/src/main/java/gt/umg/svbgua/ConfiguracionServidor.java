package gt.umg.svbgua;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Dirección del servidor (API) al que se conecta la app. Quien instala el programa no tiene un
 * servidor en su propia máquina, así que la dirección se elige desde el login y se recuerda en
 * {@code ~/.svb-gua/servidor.txt}. Orden de prioridad: lo guardado por el usuario, la variable
 * de entorno {@code SVB_API_URL}, la propiedad {@code -Dsvb.api.url} y, por último, localhost.
 */
final class ConfiguracionServidor {

    static final String POR_DEFECTO = "http://localhost:3000";

    private static final Path ARCHIVO = Path.of(System.getProperty("user.home"), ".svb-gua", "servidor.txt");

    private ConfiguracionServidor() {
    }

    static String urlActual() {
        try {
            if (Files.isRegularFile(ARCHIVO)) {
                String guardada = Files.readString(ARCHIVO, StandardCharsets.UTF_8).trim();
                if (!guardada.isEmpty()) {
                    return guardada;
                }
            }
        } catch (IOException error) {
            // Un archivo ilegible no debe impedir abrir la app: se usa la siguiente fuente.
        }
        String entorno = System.getenv("SVB_API_URL");
        if (entorno != null && !entorno.isBlank()) {
            return entorno.trim();
        }
        return System.getProperty("svb.api.url", POR_DEFECTO);
    }

    /** Acepta "192.168.1.10:3000" o "http://host:3000/"; devuelve "http://192.168.1.10:3000". */
    static String normalizar(String texto) {
        String url = texto == null ? "" : texto.trim();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("Escriba la dirección del servidor.");
        }
        if (!url.matches("(?i)^https?://.*")) {
            url = "http://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("La dirección no es válida.");
            }
            return url;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("La dirección no es válida. Ejemplo: http://192.168.1.10:3000");
        }
    }

    /** Comprueba que en esa dirección responda el servidor de SVB-GUA (GET /health). */
    static void verificar(String url) {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create(url + "/health"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> respuesta;
        try {
            respuesta = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                    .send(peticion, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La comprobación fue interrumpida.");
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo conectar con " + url
                    + ". Revise la dirección y que el servidor esté encendido.");
        }
        if (respuesta.statusCode() != 200 || !respuesta.body().contains("svb-gua-backend")) {
            throw new IllegalStateException("Esa dirección responde, pero no parece ser el servidor de SVB-GUA.");
        }
    }

    static void guardar(String url) {
        try {
            Files.createDirectories(ARCHIVO.getParent());
            Files.writeString(ARCHIVO, url, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo guardar la configuración: " + error.getMessage());
        }
    }

    /** Normaliza, verifica y guarda; devuelve la dirección final. Lanza con un mensaje listo para mostrar. */
    static String probarYGuardar(String texto) {
        String url = normalizar(texto);
        verificar(url);
        guardar(url);
        return url;
    }
}
