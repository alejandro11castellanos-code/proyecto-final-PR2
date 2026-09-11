package gt.umg.svbgua;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Busca un adelanto corto (≈30s) de una canción de un artista en catálogos
 * públicos que no requieren autenticación: iTunes Search API primero,
 * Deezer como respaldo si el artista no aparece ahí. No usamos el Web
 * Playback SDK de Spotify porque corre solo en navegador, exige cuenta
 * Premium por usuario y requiere OAuth — nada de eso encaja en un cliente
 * de escritorio Java.
 */
public final class PreviewClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public PreviewClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public static PreviewClient crear() {
        return new PreviewClient(HttpClient.newHttpClient());
    }

    /** URL de un preview de audio para el artista, o {@code null} si no se encontró en ninguna fuente. */
    public String buscarPreview(String nombreArtista) {
        String deItunes = buscarEnItunes(nombreArtista);
        return deItunes != null ? deItunes : buscarEnDeezer(nombreArtista);
    }

    private String buscarEnItunes(String nombreArtista) {
        URI uri = URI.create("https://itunes.apple.com/search?term=" + codificar(nombreArtista)
                + "&entity=song&limit=1");
        try {
            JsonNode resultados = obtenerJson(uri).path("results");
            return textoONull(primerElemento(resultados), "previewUrl");
        } catch (Exception ignored) {
            // Se intenta con Deezer a continuación.
            return null;
        }
    }

    private String buscarEnDeezer(String nombreArtista) {
        URI uri = URI.create("https://api.deezer.com/search?q=" + codificar(nombreArtista) + "&limit=1");
        try {
            JsonNode datos = obtenerJson(uri).path("data");
            return textoONull(primerElemento(datos), "preview");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JsonNode primerElemento(JsonNode arreglo) {
        return arreglo.isArray() && !arreglo.isEmpty() ? arreglo.get(0) : null;
    }

    private static String textoONull(JsonNode nodo, String campo) {
        if (nodo == null) {
            return null;
        }
        String valor = nodo.path(campo).asText(null);
        return (valor == null || valor.isBlank()) ? null : valor;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private JsonNode obtenerJson(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }
}
