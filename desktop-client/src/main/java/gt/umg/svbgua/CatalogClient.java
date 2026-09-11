package gt.umg.svbgua;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Cliente HTTP para el catálogo (artistas, localidades, conciertos). Usa
 * snake_case en el mapper para que los récords en camelCase calcen con los
 * nombres de columna que expone la API sin anotar cada campo a mano.
 */
public final class CatalogClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI baseUri;

    public CatalogClient(HttpClient httpClient, URI baseUri) {
        this.httpClient = httpClient;
        this.baseUri = baseUri;
        this.mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static CatalogClient fromEnvironment() {
        String apiUrl = System.getenv().getOrDefault("SVB_API_URL", "http://localhost:3000");
        return new CatalogClient(HttpClient.newHttpClient(), URI.create(apiUrl));
    }

    // ---- Artistas ----

    public List<Artista> listArtistas(String token) {
        return getList("/artistas", token, Artista[].class);
    }

    public Artista createArtista(String token, String nombreArtistico, String generoMusical, String paisOrigen) {
        return send("POST", "/artistas", token,
                new ArtistaRequest(nombreArtistico, generoMusical, paisOrigen), Artista.class);
    }

    public Artista updateArtista(
            String token, int id, String nombreArtistico, String generoMusical, String paisOrigen) {
        return send("PUT", "/artistas/" + id, token,
                new ArtistaRequest(nombreArtistico, generoMusical, paisOrigen), Artista.class);
    }

    public void deleteArtista(String token, int id) {
        delete("/artistas/" + id, token);
    }

    // ---- Localidades ----

    public List<Localidad> listLocalidades(String token) {
        return getList("/localidades", token, Localidad[].class);
    }

    public Localidad createLocalidad(String token, String nombre) {
        return send("POST", "/localidades", token, new LocalidadRequest(nombre), Localidad.class);
    }

    public Localidad updateLocalidad(String token, int id, String nombre) {
        return send("PUT", "/localidades/" + id, token, new LocalidadRequest(nombre), Localidad.class);
    }

    public void deleteLocalidad(String token, int id) {
        delete("/localidades/" + id, token);
    }

    // ---- Conciertos ----

    public List<Concierto> listConciertos(String token) {
        return getList("/conciertos", token, Concierto[].class);
    }

    public Concierto createConcierto(
            String token, int idArtista, String tituloEvento, String fechaConcierto, String recinto, String estado) {
        return send("POST", "/conciertos", token,
                new ConciertoRequest(idArtista, tituloEvento, fechaConcierto, recinto, estado), Concierto.class);
    }

    public Concierto updateConcierto(String token, int id, int idArtista, String tituloEvento,
            String fechaConcierto, String recinto, String estado) {
        return send("PUT", "/conciertos/" + id, token,
                new ConciertoRequest(idArtista, tituloEvento, fechaConcierto, recinto, estado), Concierto.class);
    }

    public void deleteConcierto(String token, int id) {
        delete("/conciertos/" + id, token);
    }

    // ---- Inventario (aforo) ----

    public List<Inventario> listInventario(String token, int idConcierto) {
        return getList("/conciertos/" + idConcierto + "/inventario", token, Inventario[].class);
    }

    public Inventario createInventario(String token, int idConcierto, int idLocalidad, double precio, int cantidadTotal) {
        return send("POST", "/conciertos/" + idConcierto + "/inventario", token,
                new InventarioCreateRequest(idLocalidad, precio, cantidadTotal), Inventario.class);
    }

    public Inventario updateInventario(
            String token, int idConcierto, int idInventario, double precio, int cantidadTotal) {
        return send("PUT", "/conciertos/" + idConcierto + "/inventario/" + idInventario, token,
                new InventarioUpdateRequest(precio, cantidadTotal), Inventario.class);
    }

    public void deleteInventario(String token, int idConcierto, int idInventario) {
        delete("/conciertos/" + idConcierto + "/inventario/" + idInventario, token);
    }

    // ---- Ventas ----

    public Venta crearVenta(String token, List<ItemVenta> items) {
        return send("POST", "/ventas", token, new VentaRequest(items), Venta.class);
    }

    // GET /ventas/:id/boletos no devuelve un array plano sino
    // { id_venta, boletos: [...] }, así que no puede pasar por getList().
    public List<Boleto> listBoletos(String token, int idVenta) {
        HttpResponse<String> response = execute(newRequest("GET", "/ventas/" + idVenta + "/boletos", token, null));
        try {
            return mapper.readValue(response.body(), BoletosResponse.class).boletos();
        } catch (IOException error) {
            throw new ApiException("La respuesta del servidor no es válida.", error);
        }
    }

    public void enviarBoletos(String token, int idVenta, String email) {
        send("POST", "/ventas/" + idVenta + "/enviar", token, new EnviarRequest(email), EnviarResponse.class);
    }

    // ---- infraestructura HTTP ----

    private <T> List<T> getList(String path, String token, Class<T[]> arrayType) {
        HttpResponse<String> response = execute(newRequest("GET", path, token, null));
        try {
            return List.of(mapper.readValue(response.body(), arrayType));
        } catch (IOException error) {
            throw new ApiException("La respuesta del servidor no es válida.", error);
        }
    }

    private <T> T send(String method, String path, String token, Object requestBody, Class<T> responseType) {
        String body;
        try {
            body = mapper.writeValueAsString(requestBody);
        } catch (IOException error) {
            throw new ApiException("No se pudo preparar la solicitud.", error);
        }
        HttpResponse<String> response = execute(newRequest(method, path, token, body));
        try {
            return mapper.readValue(response.body(), responseType);
        } catch (IOException error) {
            throw new ApiException("La respuesta del servidor no es válida.", error);
        }
    }

    private void delete(String path, String token) {
        execute(newRequest("DELETE", path, token, null));
    }

    private HttpRequest newRequest(String method, String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return builder.method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .build();
    }

    private HttpResponse<String> execute(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response;
            }
            throw new ApiException(readApiError(response.body(), response.statusCode()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApiException("La solicitud fue interrumpida.", error);
        } catch (IOException error) {
            throw new ApiException("No se pudo conectar con el servidor.", error);
        }
    }

    private String readApiError(String body, int statusCode) {
        try {
            JsonNode json = mapper.readTree(body);
            if (json != null) {
                String message = json.path("error").asText();
                if (!message.isBlank()) {
                    return message;
                }
            }
        } catch (IOException ignored) {
            // El servidor puede responder sin cuerpo JSON en errores inesperados.
        }
        return "El servidor respondió con un error (" + statusCode + ").";
    }

    public record Artista(int idArtista, String nombreArtistico, String generoMusical, String paisOrigen) {
    }

    public record Localidad(int idLocalidad, String nombre) {
    }

    public record Concierto(int idConcierto, int idArtista, String nombreArtistico, String tituloEvento,
            String fechaConcierto, String recinto, String estado) {
    }

    public record Inventario(int idInventario, int idConcierto, int idLocalidad, String nombreLocalidad,
            String precio, int cantidadTotal, int cantidadDisponible) {
    }

    public record ItemVenta(int idInventario, int cantidad) {
    }

    public record Venta(int idVenta, int idVendedor, String fechaVenta, String totalVenta) {
    }

    public record Boleto(int idDetalle, String tituloEvento, String nombreLocalidad, int cantidad,
            String codigo, String qr) {
    }

    private record BoletosResponse(int idVenta, List<Boleto> boletos) {
    }

    private record EnviarRequest(String email) {
    }

    private record EnviarResponse(boolean enviado, String destinatario) {
    }

    private record ArtistaRequest(String nombreArtistico, String generoMusical, String paisOrigen) {
    }

    private record LocalidadRequest(String nombre) {
    }

    private record ConciertoRequest(
            int idArtista, String tituloEvento, String fechaConcierto, String recinto, String estado) {
    }

    private record InventarioCreateRequest(int idLocalidad, double precio, int cantidadTotal) {
    }

    private record InventarioUpdateRequest(double precio, int cantidadTotal) {
    }

    private record VentaRequest(List<ItemVenta> items) {
    }

    public static final class ApiException extends RuntimeException {
        public ApiException(String message) {
            super(message);
        }

        public ApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
