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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class MtrGetAllStationSchedulesActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_all_station_schedules";

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
        String dimension = message.getPayload() != null && message.getPayload().has("dimension")
            ? message.getPayload().get("dimension").getAsString()
            : null;

        try {
            return MtrScheduleRequestQueue.submit(ACTION, () -> buildAllStationSchedulesResponse(
                message.getRequestId(),
                gateway,
                dimension
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
            return error(message.getRequestId(), "failed to build station schedules");
        }
    }

    private BeaconResponse buildAllStationSchedulesResponse(String requestId,
            MtrQueryGateway gateway,
            String dimension) {
        List<MtrDimensionSnapshot> snapshots = gateway.fetchSnapshots();
        List<DimensionOverview> overviews = gateway.fetchNetworkOverview();
        Set<String> dimensions = collectTargetDimensions(dimension, snapshots, overviews);
        if (dimensions.isEmpty()) {
            return invalidPayload(requestId, "no registered dimensions");
        }

        JsonArray dimensionArray = new JsonArray();
        for (String dimId : dimensions) {
            List<StationInfo> stations = gateway.fetchStations(dimId);
            if (stations == null || stations.isEmpty()) {
                continue;
            }
            Map<Long, String> platformNames = buildPlatformNameIndex(stations);
            Map<Long, String> routeNames = buildRouteNameIndex(dimId, overviews);
            JsonArray stationArray = new JsonArray();
            for (StationInfo station : stations) {
                if (station == null) {
                    continue;
                }
                Optional<StationTimetable> optional = gateway.fetchStationTimetable(dimId, station.getStationId(), null);
                if (!optional.isPresent()) {
                    continue;
                }
                StationTimetable timetable = optional.get();
                JsonArray platformArray = writePlatformSchedules(
                    timetable.getPlatforms(),
                    platformNames,
                    routeNames
                );
                if (platformArray.size() == 0) {
                    continue;
                }
                JsonObject stationJson = new JsonObject();
                stationJson.addProperty("stationId", station.getStationId());
                stationJson.addProperty("stationName", station.getName());
                stationJson.add("platforms", platformArray);
                stationArray.add(stationJson);
            }
            if (stationArray.size() == 0) {
                continue;
            }
            JsonObject dimensionJson = new JsonObject();
            dimensionJson.addProperty("dimension", dimId);
            dimensionJson.add("stations", stationArray);
            dimensionArray.add(dimensionJson);
        }
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("timestamp", System.currentTimeMillis());
        if (dimension != null && !dimension.isEmpty()) {
            responsePayload.addProperty("dimension", dimension);
        }
        if (dimensionArray.size() == 0) {
            responsePayload.addProperty("note", "no schedules available yet");
        }
        responsePayload.add("dimensions", dimensionArray);
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
                if (overview != null) {
                    targets.add(overview.getDimensionId());
                }
            }
        }
        return targets;
    }

    private static Map<Long, String> buildRouteNameIndex(String dimension, List<DimensionOverview> overviews) {
        Map<Long, String> names = new HashMap<>();
        if (overviews == null || overviews.isEmpty()) {
            return names;
        }
        for (DimensionOverview overview : overviews) {
            if (overview == null || !dimension.equals(overview.getDimensionId())) {
                continue;
            }
            if (overview.getRoutes() == null) {
                continue;
            }
            for (RouteSummary route : overview.getRoutes()) {
                names.putIfAbsent(route.getRouteId(), route.getName());
            }
        }
        return names;
    }

    private static Map<Long, String> buildPlatformNameIndex(List<StationInfo> stations) {
        Map<Long, String> names = new HashMap<>();
        if (stations == null) {
            return names;
        }
        for (StationInfo station : stations) {
            if (station == null || station.getPlatforms() == null) {
                continue;
            }
            for (StationPlatformInfo platform : station.getPlatforms()) {
                if (platform == null || platform.getPlatformName() == null || platform.getPlatformName().isEmpty()) {
                    continue;
                }
                names.putIfAbsent(platform.getPlatformId(), platform.getPlatformName());
            }
        }
        return names;
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
