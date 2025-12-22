package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.mtr.MtrDimensionSnapshot;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.ScheduleEntry;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationPlatformInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class MtrGetStationScheduleActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_station_schedule";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        MtrQueryGateway gateway = gateway();
        if (!gateway.isReady()) {
            return notReady(message.getRequestId());
        }
        JsonObject payload = message.getPayload();
        if (payload == null || !payload.has("stationId")) {
            return invalidPayload(message.getRequestId(), "stationId is required");
        }
        long stationId = payload.get("stationId").getAsLong();
        String dimension = payload.has("dimension") ? payload.get("dimension").getAsString() : null;
        Long platformId = payload.has("platformId") ? payload.get("platformId").getAsLong() : null;

        try {
            return MtrScheduleRequestQueue.submit(ACTION, () -> buildStationScheduleResponse(
                message.getRequestId(),
                gateway,
                stationId,
                dimension,
                platformId
            ));
        } catch (MtrScheduleRequestQueue.QueueRejectedException e) {
            BeaconProviderMod.LOGGER.warn("Rejecting {} request", ACTION, e);
            return busy(message.getRequestId(), "schedule requests are busy right now");
        } catch (TimeoutException e) {
            BeaconProviderMod.LOGGER.warn("Timeout waiting for {} queue", ACTION, e);
            return busy(message.getRequestId(), "schedule service busy");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            BeaconProviderMod.LOGGER.warn("Interrupted while waiting for {} queue", ACTION, e);
            return busy(message.getRequestId(), "schedule service interrupted");
        } catch (ExecutionException e) {
            BeaconProviderMod.LOGGER.error("Failed to build {} response", ACTION, e.getCause());
            return error(message.getRequestId(), "failed to build station timetable");
        }
    }

    private BeaconResponse buildStationScheduleResponse(String requestId,
            MtrQueryGateway gateway,
            long stationId,
            String dimension,
            Long platformId) {
        List<MtrDimensionSnapshot> snapshots = gateway.fetchSnapshots();
        List<DimensionOverview> overviews = gateway.fetchNetworkOverview();
        Set<String> targetDimensions = collectTargetDimensions(dimension, snapshots, overviews);
        if (targetDimensions.isEmpty()) {
            return invalidPayload(requestId, "no registered dimensions");
        }

        Map<String, Map<Long, String>> routeNamesByDimension = buildRouteNameIndex(overviews);
        Map<String, Map<Long, String>> platformNamesByDimension = buildPlatformNameIndex(gateway.fetchStations(null));

        JsonArray timetablesArray = new JsonArray();
        for (String dimId : targetDimensions) {
            Optional<StationTimetable> optional = gateway.fetchStationTimetable(dimId, stationId, platformId);
            if (!optional.isPresent()) {
                continue;
            }
            StationTimetable timetable = optional.get();
            JsonObject entry = new JsonObject();
            entry.addProperty("dimension", dimId);
            entry.add("platforms", writePlatformSchedules(
                timetable.getPlatforms(),
                platformNamesByDimension.getOrDefault(dimId, Collections.emptyMap()),
                routeNamesByDimension.getOrDefault(dimId, Collections.emptyMap())
            ));
            timetablesArray.add(entry);
        }
        if (timetablesArray.size() == 0) {
            return invalidPayload(requestId, "station timetable unavailable");
        }

        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("timestamp", System.currentTimeMillis());
        responsePayload.addProperty("stationId", stationId);
        if (dimension != null && !dimension.isEmpty()) {
            responsePayload.addProperty("dimension", dimension);
        }
        responsePayload.add("timetables", timetablesArray);
        return ok(requestId, responsePayload);
    }

    private static Set<String> collectTargetDimensions(String requestedDimension,
            List<MtrDimensionSnapshot> snapshots,
            List<DimensionOverview> overviews) {
        Set<String> targets = new LinkedHashSet<>();
        if (requestedDimension != null && !requestedDimension.isEmpty()) {
            targets.add(requestedDimension);
            return targets;
        }
        if (snapshots != null) {
            for (MtrDimensionSnapshot snapshot : snapshots) {
                if (snapshot != null) {
                    targets.add(snapshot.getDimensionId());
                }
            }
        }
        if (targets.isEmpty() && overviews != null) {
            for (DimensionOverview overview : overviews) {
                targets.add(overview.getDimensionId());
            }
        }
        return targets;
    }

    private static Map<String, Map<Long, String>> buildRouteNameIndex(List<DimensionOverview> overviews) {
        Map<String, Map<Long, String>> index = new HashMap<>();
        if (overviews == null) {
            return index;
        }
        for (DimensionOverview overview : overviews) {
            Map<Long, String> map = index.computeIfAbsent(overview.getDimensionId(), key -> new HashMap<>());
            if (overview.getRoutes() == null) {
                continue;
            }
            for (RouteSummary route : overview.getRoutes()) {
                map.putIfAbsent(route.getRouteId(), route.getName());
            }
        }
        return index;
    }

    private static Map<String, Map<Long, String>> buildPlatformNameIndex(List<StationInfo> stations) {
        Map<String, Map<Long, String>> index = new HashMap<>();
        if (stations == null) {
            return index;
        }
        for (StationInfo station : stations) {
            if (station == null) {
                continue;
            }
            Map<Long, String> names = index.computeIfAbsent(station.getDimensionId(), key -> new HashMap<>());
            for (StationPlatformInfo platform : station.getPlatforms()) {
                if (platform == null || platform.getPlatformName() == null || platform.getPlatformName().isEmpty()) {
                    continue;
                }
                names.putIfAbsent(platform.getPlatformId(), platform.getPlatformName());
            }
        }
        return index;
    }

    private static JsonArray writePlatformSchedules(List<PlatformTimetable> platforms,
            Map<Long, String> platformNames,
            Map<Long, String> routeNames) {
        JsonArray array = new JsonArray();
        if (platforms == null || platforms.isEmpty()) {
            return array;
        }
        for (PlatformTimetable platform : platforms) {
            if (platform == null) {
                continue;
            }
            JsonArray entries = new JsonArray();
            List<ScheduleEntry> scheduleEntries = platform.getEntries();
            if (scheduleEntries != null) {
                for (ScheduleEntry entry : scheduleEntries) {
                    if (entry == null) {
                        continue;
                    }
                    entries.add(MtrJsonWriter.writeScheduleEntry(entry, routeNames));
                }
            }
            if (entries.size() == 0) {
                continue;
            }
            JsonObject platformJson = new JsonObject();
            platformJson.addProperty("platformId", platform.getPlatformId());
            String platformName = platformNames.get(platform.getPlatformId());
            if (platformName != null && !platformName.isEmpty()) {
                platformJson.addProperty("platformName", platformName);
            }
            platformJson.add("entries", entries);
            array.add(platformJson);
        }
        return array;
    }
}
