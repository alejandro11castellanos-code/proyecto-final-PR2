package gt.umg.svbgua;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Cliente HTTP mínimo para iniciar sesión contra la API REST. */
public final class AuthClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI loginUri;

    public AuthClient(HttpClient httpClient, ObjectMapper mapper, URI apiBaseUri) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.loginUri = apiBaseUri.resolve("/auth/login");
    }

    public static AuthClient fromEnvironment() {
        String apiUrl = System.getenv().getOrDefault("SVB_API_URL", "http://localhost:3000");
        return new AuthClient(HttpClient.newHttpClient(), new ObjectMapper(), URI.create(apiUrl));
    }

    public LoginResult login(String username, String password) {
        try {
            String body = mapper.writeValueAsString(new LoginRequest(username, password));
            HttpRequest request = HttpRequest.newBuilder(loginUri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());

            if (response.statusCode() != 200) {
                String apiMessage = json.path("error").asText("No fue posible iniciar sesión.");
                throw new AuthException(apiMessage);
            }

            Usuario usuario = new Usuario(
                    json.path("usuario").path("id_usuario").asInt(),
                    json.path("usuario").path("nombre_usuario").asText(),
                    json.path("usuario").path("nombre_completo").asText(),
                    json.path("usuario").path("rol").asText());
            return new LoginResult(json.path("token").asText(), usuario);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AuthException("La solicitud fue interrumpida.", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new AuthException("No se pudo conectar con el servidor.", error);
        }
    }

    private record LoginRequest(String nombre_usuario, String contrasena) {
    }

    public record LoginResult(String token, Usuario usuario) {
    }

    public record Usuario(int idUsuario, String nombreUsuario, String nombreCompleto, String rol) {
    }

    public static final class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }

        public AuthException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
