package com.minelatino.afkfarm.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Asynchronous API adapter. Authorization is always sent in a header and is never logged. */
public final class AiAssistantApi {
    public record Reply(String conversationId, String id, String content, long createdAt) {}
    public static final class ApiFailure extends RuntimeException {
        private final int status;
        ApiFailure(int status, String message) { super(message); this.status = status; }
        public int status() { return status; }
    }
    private record AssistantCredential(String token, long expiresAt, String parentToken) {}

    private static final Gson GSON = new Gson();
    private static final int MAX_RESPONSE_CHARS = 64 * 1024;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final AtomicReference<CompletableFuture<?>> network = new AtomicReference<>();
    private volatile AssistantCredential credential;

    public CompletableFuture<Reply> send(CosmeticsSessionBridge.LinkedSession session, String conversationId,
                                         String requestId, String message) {
        return credential(session).thenCompose(token -> chat(session, token, conversationId, requestId, message, true));
    }

    public void cancel() {
        CompletableFuture<?> pending = network.getAndSet(null);
        if (pending != null) pending.cancel(true);
    }

    public void clearCredential() { credential = null; }

    private CompletableFuture<String> credential(CosmeticsSessionBridge.LinkedSession session) {
        AssistantCredential current = credential;
        if (current != null && Objects.equals(current.parentToken(), session.token())
                && current.expiresAt() - System.currentTimeMillis() > 30_000) return CompletableFuture.completedFuture(current.token());
        return post(session.apiBaseUrl() + "/v1/ai/token", "{}", session.token()).thenApply(response -> {
            if (response.statusCode() != 201) throw failure(response);
            JsonObject json = parse(response.body());
            String token = requiredString(json, "token");
            long expiresAt = json.has("expiresAt") ? json.get("expiresAt").getAsLong() : 0;
            if (token.length() < 32 || expiresAt <= System.currentTimeMillis()) throw new ApiFailure(502, "Credencial del asistente inválida");
            credential = new AssistantCredential(token, expiresAt, session.token());
            return token;
        });
    }

    private CompletableFuture<Reply> chat(CosmeticsSessionBridge.LinkedSession session, String token,
                                          String conversationId, String requestId, String message, boolean retry) {
        JsonObject body = new JsonObject();
        body.addProperty("requestId", requestId);
        if (conversationId == null) body.add("conversationId", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("conversationId", conversationId);
        body.addProperty("message", message);
        return post(session.apiBaseUrl() + "/v1/ai/chat", body.toString(), token).thenCompose(response -> {
            if (response.statusCode() == 401 && retry) {
                credential = null;
                return credential(session).thenCompose(next -> chat(session, next, conversationId, requestId, message, false));
            }
            if (response.statusCode() != 200) throw failure(response);
            JsonObject json = parse(response.body()), answer = json.getAsJsonObject("message");
            if (answer == null) throw new ApiFailure(502, "Respuesta del asistente inválida");
            return CompletableFuture.completedFuture(new Reply(requiredString(json, "conversationId"),
                    requiredString(answer, "id"), requiredString(answer, "content"),
                    answer.has("createdAt") ? answer.get("createdAt").getAsLong() : System.currentTimeMillis()));
        });
    }

    private CompletableFuture<HttpResponse<String>> post(String url, String json, String token) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(65))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .header("Accept", "application/json").header("X-MineLatino-Client", "afk-farm/0.1")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        CompletableFuture<HttpResponse<String>> future = http.sendAsync(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        network.set(future);
        return future.whenComplete((ignored, error) -> network.compareAndSet(future, null)).thenApply(response -> {
            if (response.body() != null && response.body().length() > MAX_RESPONSE_CHARS)
                throw new ApiFailure(502, "Respuesta del asistente demasiado grande");
            return response;
        });
    }

    private static JsonObject parse(String value) {
        try {
            JsonObject result = GSON.fromJson(value, JsonObject.class);
            if (result == null) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException error) { throw new ApiFailure(502, "Respuesta del servidor inválida"); }
    }

    private static ApiFailure failure(HttpResponse<String> response) {
        String message = switch (response.statusCode()) {
            case 401, 403 -> "Vuelve a vincular tu cuenta MineLatino";
            case 408, 504 -> "El asistente tardó demasiado en responder";
            case 429 -> "El asistente está ocupado; inténtalo más tarde";
            case 503 -> "El asistente de IA no está disponible";
            default -> "No se pudo conectar con el asistente";
        };
        try {
            JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
            if (json != null && json.has("error") && json.get("error").getAsString().length() <= 180)
                message = json.get("error").getAsString();
        } catch (RuntimeException ignored) {}
        return new ApiFailure(response.statusCode(), message);
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()) throw new ApiFailure(502, "Respuesta del servidor inválida");
        return object.get(name).getAsString();
    }
}
