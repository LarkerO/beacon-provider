package com.hydroline.beacon.provider.forge.create;

import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.create.CreateRealtimeSnapshot;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

final class CreateRealtimeChannel {
    private static final long DEFAULT_INTERVAL_MILLIS = 500L;

    private final Supplier<MinecraftServer> serverSupplier;
    private final Consumer<CreateRealtimeSnapshot> snapshotCallback;
    private final AtomicReference<CreateRealtimeSnapshot> snapshotRef = new AtomicReference<CreateRealtimeSnapshot>(CreateRealtimeSnapshot.empty());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    CreateRealtimeChannel(Supplier<MinecraftServer> serverSupplier, Consumer<CreateRealtimeSnapshot> snapshotCallback) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier");
        this.snapshotCallback = snapshotCallback == null ? snapshot -> {} : snapshotCallback;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Create-Realtime-Channel");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0L, DEFAULT_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    void stop() {
        running.set(false);
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
        }
        scheduler = null;
        snapshotRef.set(CreateRealtimeSnapshot.empty());
    }

    CreateRealtimeSnapshot getSnapshot() {
        return snapshotRef.get();
    }

    private void tick() {
        if (!running.get() || !inFlight.compareAndSet(false, true)) {
            return;
        }
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            inFlight.set(false);
            return;
        }
        server.execute(() -> {
            try {
                CreateRealtimeSnapshot snapshot = CreateRealtimeSnapshotBuilder.capture(server);
                if (snapshot != null) {
                    snapshotRef.set(snapshot);
                    snapshotCallback.accept(snapshot);
                }
            } catch (Throwable throwable) {
                BeaconProviderMod.LOGGER.debug("Failed to capture Create realtime snapshot", throwable);
            } finally {
                inFlight.set(false);
            }
        });
    }
}
