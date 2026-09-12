package com.minelatino.afkfarm.client;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;

/** Owns the server lease and exposes a safe read-only balance to the UI. */
public final class AfkUsageController {
    public enum State { CHECKING, READY, EMPTY, ACTIVE, ERROR, UNLINKED }
    private static final AfkUsageController INSTANCE = new AfkUsageController();
    private static final long HEARTBEAT_MS = 20_000;
    private static final long OFFLINE_LEASE_MS = 65_000;
    private final AfkUsageApi api = new AfkUsageApi();
    private final AtomicBoolean requestRunning = new AtomicBoolean();
    private volatile State state = State.CHECKING;
    private volatile long confirmedSeconds = -1;
    private volatile long confirmedAt;
    private volatile long lastHeartbeatAt;
    private volatile String sessionId;
    private volatile String message = "Comprobando tiempo disponible…";

    public static AfkUsageController instance() { return INSTANCE; }
    public State state() { return state; }
    public boolean canStart() { return state == State.READY && remainingSeconds() > 0; }
    public String message() { return message; }
    public long remainingSeconds() {
        if (confirmedSeconds < 0) return -1;
        if (state != State.ACTIVE) return confirmedSeconds;
        return Math.max(0, confirmedSeconds - Math.max(0, (System.currentTimeMillis() - confirmedAt) / 1000));
    }

    public void refresh() {
        if (!requestRunning.compareAndSet(false, true)) return;
        var linked = CosmeticsSessionBridge.current();
        if (linked.isEmpty()) { requestRunning.set(false); state = State.UNLINKED; confirmedSeconds = -1; message = "Vuelve a vincular tu cuenta MineLatino"; return; }
        state = State.CHECKING; message = "Comprobando tiempo disponible…";
        api.status(linked.get()).whenComplete((usage, error) -> Minecraft.getInstance().execute(() -> {
            requestRunning.set(false);
            if (error != null) fail(error); else apply(usage);
        }));
    }

    public void start(Runnable granted, java.util.function.Consumer<String> denied) {
        if (!requestRunning.compareAndSet(false, true)) return;
        var linked = CosmeticsSessionBridge.current();
        if (linked.isEmpty()) { requestRunning.set(false); state = State.UNLINKED; message = "Vuelve a vincular tu cuenta MineLatino"; denied.accept(message); return; }
        state = State.CHECKING; message = "Validando tiempo de uso…";
        api.start(linked.get()).whenComplete((usage, error) -> Minecraft.getInstance().execute(() -> {
            requestRunning.set(false);
            if (error != null) { fail(error); denied.accept(message); return; }
            apply(usage); lastHeartbeatAt = confirmedAt;
            if (state == State.ACTIVE) granted.run(); else denied.accept(message);
        }));
    }

    public void tick(boolean localActive) {
        if (!localActive || state != State.ACTIVE || sessionId == null) return;
        long now = System.currentTimeMillis();
        if (remainingSeconds() <= 0) { AfkFarmClient.instance().cancel("Tiempo de AFK Farm agotado"); return; }
        if (now - confirmedAt > OFFLINE_LEASE_MS) { AfkFarmClient.instance().cancel("No se pudo renovar el tiempo de AFK Farm"); return; }
        if (now - lastHeartbeatAt < HEARTBEAT_MS || !requestRunning.compareAndSet(false, true)) return;
        lastHeartbeatAt = now;
        var linked = CosmeticsSessionBridge.current();
        if (linked.isEmpty()) { requestRunning.set(false); AfkFarmClient.instance().cancel("Vuelve a vincular tu cuenta MineLatino"); return; }
        String id = sessionId;
        api.heartbeat(linked.get(), id).whenComplete((usage, error) -> Minecraft.getInstance().execute(() -> {
            requestRunning.set(false);
            if (error != null) { message = rootMessage(error); return; }
            apply(usage);
            if (usage.exhausted()) AfkFarmClient.instance().cancel("Tiempo de AFK Farm agotado");
        }));
    }

    public void stop() {
        String id = sessionId;
        sessionId = null;
        if (id == null) return;
        state = confirmedSeconds > 0 ? State.READY : State.EMPTY;
        var linked = CosmeticsSessionBridge.current();
        if (linked.isEmpty()) return;
        api.stop(linked.get(), id).whenComplete((usage, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null) apply(usage); else message = rootMessage(error);
        }));
    }

    private void apply(AfkUsageApi.Usage usage) {
        confirmedSeconds = usage.remainingSeconds(); confirmedAt = System.currentTimeMillis(); sessionId = usage.sessionId();
        state = usage.active() ? State.ACTIVE : usage.allowed() ? State.READY : State.EMPTY;
        message = state == State.EMPTY ? "Sin tiempo disponible" : state == State.ACTIVE ? "AFK Farm activo" : "Tiempo disponible";
    }
    private void fail(Throwable error) { state = State.ERROR; message = rootMessage(error); }
    private static String rootMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? "No se pudo comprobar el tiempo de AFK Farm" : value.getMessage();
    }
}
