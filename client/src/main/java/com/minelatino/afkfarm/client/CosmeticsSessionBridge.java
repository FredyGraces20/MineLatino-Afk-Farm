package com.minelatino.afkfarm.client;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;

/** Runtime-only bridge: AFK Farm reuses CosmeticsClient without bundling or copying its token. */
public final class CosmeticsSessionBridge {
    public record LinkedSession(String token, String accountId, String name, long expiresAt, String apiBaseUrl) {
        public boolean expired() { return System.currentTimeMillis() >= expiresAt; }
    }

    private CosmeticsSessionBridge() {}

    public static Optional<LinkedSession> current() {
        try {
            Class<?> type = Class.forName("com.minelatino.cosmetics.client.CosmeticsClient");
            Object client = type.getMethod("instance").invoke(null);
            Object auth = type.getMethod("auth").invoke(client);
            Object session = auth.getClass().getMethod("session").invoke(auth);
            boolean connected = (boolean) auth.getClass().getMethod("isConnected").invoke(auth);
            if (!connected || session == null) return Optional.empty();
            Object api = type.getMethod("api").invoke(client);
            String baseUrl = String.valueOf(api.getClass().getMethod("getBaseUrl").invoke(api));
            LinkedSession result = new LinkedSession(string(session, "token"), string(session, "uuid"),
                    string(session, "name"), number(session, "expiresAt"), safeBaseUrl(baseUrl));
            return result.token().length() >= 32 && !result.accountId().isBlank() && !result.expired()
                    ? Optional.of(result) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String string(Object value, String accessor) throws ReflectiveOperationException {
        return String.valueOf(value.getClass().getMethod(accessor).invoke(value));
    }

    private static long number(Object value, String accessor) throws ReflectiveOperationException {
        return ((Number) value.getClass().getMethod(accessor).invoke(value)).longValue();
    }

    private static String safeBaseUrl(String value) {
        URI uri = URI.create(value);
        boolean loopback = "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equalsIgnoreCase(uri.getHost()));
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !loopback) throw new IllegalArgumentException("API insegura");
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("API inválida");
        return value.replaceAll("/+$", "");
    }
}
