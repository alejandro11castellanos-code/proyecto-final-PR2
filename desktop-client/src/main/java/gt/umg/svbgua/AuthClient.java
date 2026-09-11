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

            if (response.statusCode() != 200) {
                throw new AuthException(readApiError(response.body()));
            }

            return readLoginResult(response.body());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AuthException("La solicitud fue interrumpida.", error);
        } catch (IOException | IllegalArgumentException error) {
            throw new AuthException("No se pudo conectar con el servidor.", error);
        }
    }

    private String readApiError(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            if (json != null) {
                String message = json.path("error").asText();
                if (!message.isBlank()) {
                    return message;
                }
            }
        } catch (IOException ignored) {
            // El servidor puede responder texto o HTML si existe un fallo externo.
        }
        return "No fue posible iniciar sesión.";
    }

    private LoginResult readLoginResult(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            JsonNode user = json == null ? null : json.get("usuario");
            String token = requiredText(json, "token");
            String username = requiredText(user, "nombre_usuario");
            String fullName = requiredText(user, "nombre_completo");
            String role = requiredText(user, "rol");
            JsonNode id = user == null ? null : user.get("id_usuario");

            if (id == null || !id.canConvertToInt() || id.asInt() <= 0) {
                throw new AuthException("La respuesta del servidor está incompleta.");
            }

            return new LoginResult(
                    token, new Usuario(id.asInt(), username, fullName, role));
        } catch (IOException error) {
            throw new AuthException("La respuesta del servidor no es válida.", error);
        }
    }

    private static String requiredText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new AuthException("La respuesta del servidor está incompleta.");
        }
        return value.asText();
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
