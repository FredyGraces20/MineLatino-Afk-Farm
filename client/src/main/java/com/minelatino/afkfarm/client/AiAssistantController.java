package com.minelatino.afkfarm.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;

/** One conversation owner for every screen instance opened during this game process. */
public final class AiAssistantController {
    public record Message(String id, String role, String content, long createdAt) {}
    private static final AiAssistantController INSTANCE = new AiAssistantController();
    public static final int MAX_MESSAGE_CHARS = 4000;

    private final AiAssistantApi api = new AiAssistantApi();
    private final List<Message> messages = new ArrayList<>();
    private String conversationId;
    private String error = "";
    private boolean busy;
    private long generation;

    private AiAssistantController() {}
    public static AiAssistantController instance() { return INSTANCE; }

    public synchronized List<Message> messages() { return List.copyOf(messages); }
    public synchronized boolean busy() { return busy; }
    public synchronized String error() { return error; }
    public synchronized String accountLabel() {
        return CosmeticsSessionBridge.current().map(session -> "Cuenta: " + session.name()).orElse("Cuenta: no vinculada");
    }
    public synchronized String connectionLabel() {
        if (busy) return "Conectando / recibiendo respuesta";
        if (!error.isBlank()) return "Error de conexión";
        return CosmeticsSessionBridge.current().isPresent() ? "Conectado" : "Sin sesión";
    }

    public void send(String raw) {
        String message = raw == null ? "" : raw.trim();
        if (message.isBlank() || message.length() > MAX_MESSAGE_CHARS) {
            synchronized (this) { error = message.length() > MAX_MESSAGE_CHARS
                    ? "El mensaje admite hasta " + MAX_MESSAGE_CHARS + " caracteres" : "Escribe un mensaje"; }
            return;
        }
        CosmeticsSessionBridge.LinkedSession session = CosmeticsSessionBridge.current().orElse(null);
        if (session == null) { synchronized (this) { error = "Vuelve a vincular tu cuenta MineLatino"; } return; }
        final long requestGeneration;
        final String currentConversation;
        synchronized (this) {
            if (busy) return;
            busy = true; error = ""; requestGeneration = ++generation; currentConversation = conversationId;
            messages.add(new Message(UUID.randomUUID().toString(), "user", message, System.currentTimeMillis()));
        }
        api.send(session, currentConversation, UUID.randomUUID().toString(), message).whenComplete((reply, throwable) ->
                Minecraft.getInstance().execute(() -> complete(requestGeneration, reply, throwable)));
    }

    private synchronized void complete(long requestGeneration, AiAssistantApi.Reply reply, Throwable throwable) {
        if (generation != requestGeneration) return;
        busy = false;
        if (throwable != null) {
            Throwable cause = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
            if (cause instanceof java.util.concurrent.CancellationException) error = "Respuesta detenida";
            else error = cause.getMessage() == null ? "No se pudo conectar con el asistente" : cause.getMessage();
            return;
        }
        conversationId = reply.conversationId();
        messages.add(new Message(reply.id(), "assistant", reply.content(), reply.createdAt()));
    }

    public synchronized void stop() {
        if (!busy) return;
        generation++; busy = false; error = "Respuesta detenida"; api.cancel();
    }

    public synchronized void newConversation() {
        if (busy) { generation++; busy = false; api.cancel(); }
        messages.clear(); conversationId = null; error = "";
    }
}
