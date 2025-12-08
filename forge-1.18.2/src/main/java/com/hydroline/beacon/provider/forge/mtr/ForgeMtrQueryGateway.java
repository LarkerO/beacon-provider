package com.hydroline.beacon.provider.forge.mtr;

import com.hydroline.beacon.provider.mtr.MtrDataMapper;
import com.hydroline.beacon.provider.mtr.MtrDimensionSnapshot;
import com.hydroline.beacon.provider.mtr.MtrModels.DepotInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.FareAreaInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodePage;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteDetail;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.TrainStatus;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.mtr.MtrRailwayDataAccess;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import mtr.data.RailwayData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForgeMtrQueryGateway implements MtrQueryGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForgeMtrQueryGateway.class);
    private final Supplier<MinecraftServer> serverSupplier;

    public ForgeMtrQueryGateway(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    @Override
    public boolean isReady() {
        return !captureSnapshots().isEmpty();
    }

    @Override
    public List<DimensionOverview> fetchNetworkOverview() {
        return MtrDataMapper.buildNetworkOverview(captureSnapshots());
    }

    @Override
    public Optional<RouteDetail> fetchRouteDetail(String dimensionId, long routeId) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .flatMap(snapshot -> MtrDataMapper.buildRouteDetail(snapshot, routeId));
    }

    @Override
    public List<DepotInfo> fetchDepots(String dimensionId) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        if (dimensionId == null || dimensionId.isEmpty()) {
            return snapshots.stream()
                .flatMap(snapshot -> MtrDataMapper.buildDepots(snapshot).stream())
                .collect(Collectors.toList());
        }
        return findSnapshot(snapshots, dimensionId)
            .map(MtrDataMapper::buildDepots)
            .orElseGet(Collections::emptyList);
    }

    @Override
    public List<FareAreaInfo> fetchFareAreas(String dimensionId) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .map(MtrDataMapper::buildFareAreas)
            .orElseGet(Collections::emptyList);
    }

    @Override
    public NodePage fetchNodes(String dimensionId, String cursor, int limit) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .map(snapshot -> MtrDataMapper.buildNodePage(snapshot, cursor, limit))
            .orElseGet(() -> new NodePage(dimensionId == null ? "" : dimensionId, Collections.emptyList(), null));
    }

    @Override
    public Optional<StationTimetable> fetchStationTimetable(String dimensionId, long stationId, Long platformId) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .flatMap(snapshot -> MtrDataMapper.buildStationTimetable(snapshot, stationId, platformId));
    }

    @Override
    public List<StationInfo> fetchStations(String dimensionId) {
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        if (dimensionId == null || dimensionId.isEmpty()) {
            return snapshots.stream()
                .flatMap(snapshot -> MtrDataMapper.buildStations(snapshot).stream())
                .collect(Collectors.toList());
        }
        return findSnapshot(snapshots, dimensionId)
            .map(MtrDataMapper::buildStations)
            .orElseGet(Collections::emptyList);
    }

    @Override
    public List<TrainStatus> fetchRouteTrains(String dimensionId, long routeId) {
        if (routeId <= 0) {
            return Collections.emptyList();
        }
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .map(snapshot -> MtrDataMapper.buildRouteTrains(snapshot, routeId))
            .orElseGet(Collections::emptyList);
    }

    @Override
    public List<TrainStatus> fetchDepotTrains(String dimensionId, long depotId) {
        if (depotId <= 0) {
            return Collections.emptyList();
        }
        List<MtrDimensionSnapshot> snapshots = captureSnapshots();
        return findSnapshot(snapshots, dimensionId)
            .map(snapshot -> MtrDataMapper.buildDepotTrains(snapshot, depotId))
            .orElseGet(Collections::emptyList);
    }

    private List<MtrDimensionSnapshot> captureSnapshots() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) {
            return Collections.emptyList();
        }
        List<MtrDimensionSnapshot> snapshots = new ArrayList<>();
        try {
            for (ServerLevel level : server.getAllLevels()) {
                try {
                    final RailwayData data = MtrRailwayDataAccess.resolve(level);
                    if (data != null) {
                        snapshots.add(new MtrDimensionSnapshot(resolveDimensionId(level), data));
                    }
                } catch (Throwable throwable) {
                    ResourceLocation id = level.dimension().location();
                    LOGGER.debug("Failed to sample MTR data for {}", id, throwable);
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Unable to enumerate Forge server levels", throwable);
            return Collections.emptyList();
        }
        return snapshots;
    }

    private static String resolveDimensionId(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private Optional<MtrDimensionSnapshot> findSnapshot(List<MtrDimensionSnapshot> snapshots, String dimensionId) {
        if (snapshots.isEmpty() || dimensionId == null || dimensionId.isEmpty()) {
            return Optional.empty();
        }
        return snapshots.stream()
            .filter(snapshot -> snapshot.getDimensionId().equals(dimensionId))
            .findFirst();
    }
}
