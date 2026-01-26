package com.hydroline.beacon.provider.forge.create;

import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.create.CreateDatabase;
import com.hydroline.beacon.provider.create.CreateNetworkSnapshot;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.graph.TrackGraph;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;

final class CreateStaticSnapshotService {
    private static final long DEFAULT_INTERVAL_MILLIS = 5 * 60 * 1000L;

    private final Supplier<MinecraftServer> serverSupplier;
    private final CreateDatabase database;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    CreateStaticSnapshotService(Supplier<MinecraftServer> serverSupplier, CreateDatabase database) {
        this.serverSupplier = serverSupplier;
        this.database = database;
    }

    void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Create-Static-Cache");
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
                refresh(server);
            } catch (Throwable throwable) {
                BeaconProviderMod.LOGGER.debug("Failed to refresh Create static snapshot", throwable);
            } finally {
                inFlight.set(false);
            }
        });
    }

    private void refresh(MinecraftServer server) {
        RailwaySavedData data = RailwaySavedData.load(server);
        if (data == null || database == null) {
            return;
        }
        Map<String, Integer> checksums = database.loadGraphChecksums();
        Set<String> seen = new HashSet<String>();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TrackGraph> entry : data.getTrackNetworks().entrySet()) {
            String graphId = entry.getKey().toString();
            TrackGraph graph = entry.getValue();
            if (graph == null) {
                continue;
            }
            seen.add(graphId);
            int checksum = graph.getChecksum();
            Integer existing = checksums.get(graphId);
            if (existing != null && existing.intValue() == checksum) {
                continue;
            }
            CreateNetworkSnapshot snapshot = CreateGraphSnapshotBuilder.build(graph, now);
            database.upsertGraph(snapshot);
        }
        for (String graphId : checksums.keySet()) {
            if (!seen.contains(graphId)) {
                database.deleteGraph(graphId);
            }
        }
    }
}
