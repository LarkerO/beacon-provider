package com.hydroline.beacon.provider.mtr;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrModels.Bounds;
import com.hydroline.beacon.provider.mtr.MtrModels.DepotInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.FareAreaInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodeInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodePage;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.PlatformTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteDetail;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteNode;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteSummary;
import com.hydroline.beacon.provider.mtr.MtrModels.ScheduleEntry;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationPlatformInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.TrainStatus;
import java.util.List;

/**
 * Converts DTOs into the JSON schema expected by Bukkit / website callers.
 */
public final class MtrJsonWriter {
    private MtrJsonWriter() {
    }

    public static JsonArray writeDimensionOverview(List<DimensionOverview> dimensions) {
        JsonArray array = new JsonArray();
        if (dimensions == null) {
            return array;
        }
        for (DimensionOverview overview : dimensions) {
            if (overview == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("dimension", overview.getDimensionId());
            JsonArray routes = new JsonArray();
            for (RouteSummary summary : overview.getRoutes()) {
                routes.add(writeRouteSummary(summary));
            }
            json.add("routes", routes);

            JsonArray depots = new JsonArray();
            for (DepotInfo depot : overview.getDepots()) {
                depots.add(writeDepotInfo(depot));
            }
            json.add("depots", depots);

            JsonArray fareAreas = new JsonArray();
            for (FareAreaInfo info : overview.getFareAreas()) {
                fareAreas.add(writeFareAreaInfo(info));
            }
            json.add("fareAreas", fareAreas);
            array.add(json);
        }
        return array;
    }

    public static JsonObject writeRouteDetail(RouteDetail detail) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", detail.getDimensionId());
        json.addProperty("routeId", detail.getRouteId());
        json.addProperty("name", detail.getName());
        json.addProperty("color", detail.getColor());
        json.addProperty("routeType", detail.getRouteType());
        JsonArray nodes = new JsonArray();
        for (RouteNode node : detail.getNodes()) {
            nodes.add(writeRouteNode(node));
        }
        json.add("nodes", nodes);
        return json;
    }

    public static JsonArray writeDepots(List<DepotInfo> depots) {
        JsonArray array = new JsonArray();
        if (depots != null) {
            for (DepotInfo depot : depots) {
                array.add(writeDepotInfo(depot));
            }
        }
        return array;
    }

    public static JsonArray writeStations(List<StationInfo> stations) {
        JsonArray array = new JsonArray();
        if (stations != null) {
            for (StationInfo station : stations) {
                array.add(writeStationInfo(station));
            }
        }
        return array;
    }

    public static JsonArray writeFareAreas(List<FareAreaInfo> fareAreas) {
        JsonArray array = new JsonArray();
        if (fareAreas != null) {
            for (FareAreaInfo info : fareAreas) {
                array.add(writeFareAreaInfo(info));
            }
        }
        return array;
    }

    public static JsonArray writeTrainStatuses(List<TrainStatus> statuses) {
        JsonArray array = new JsonArray();
        if (statuses != null) {
            for (TrainStatus status : statuses) {
                array.add(writeTrainStatus(status));
            }
        }
        return array;
    }

    public static JsonObject writeNodePage(NodePage page) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", page.getDimensionId());
        JsonArray nodes = new JsonArray();
        for (NodeInfo node : page.getNodes()) {
            nodes.add(writeNodeInfo(node));
        }
        json.add("nodes", nodes);
        page.getNextCursor().ifPresent(cursor -> json.addProperty("nextCursor", cursor));
        json.addProperty("hasMore", page.getNextCursor().isPresent());
        return json;
    }

    public static JsonObject writeStationTimetable(StationTimetable timetable) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", timetable.getDimensionId());
        json.addProperty("stationId", timetable.getStationId());
        JsonArray platforms = new JsonArray();
        for (PlatformTimetable platform : timetable.getPlatforms()) {
            platforms.add(writePlatformTimetable(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writeRouteSummary(RouteSummary summary) {
        JsonObject json = new JsonObject();
        json.addProperty("routeId", summary.getRouteId());
        json.addProperty("name", summary.getName());
        json.addProperty("color", summary.getColor());
        json.addProperty("transportMode", summary.getTransportMode());
        json.addProperty("routeType", summary.getRouteType());
        json.addProperty("hidden", summary.isHidden());
        JsonArray platforms = new JsonArray();
        for (PlatformSummary platform : summary.getPlatforms()) {
            platforms.add(writePlatformSummary(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writePlatformSummary(PlatformSummary summary) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", summary.getPlatformId());
        json.addProperty("stationId", summary.getStationId());
        json.addProperty("stationName", summary.getStationName());
        if (summary.getBounds() != null) {
            json.add("bounds", writeBounds(summary.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(summary.getInterchangeRouteIds()));
        return json;
    }

    private static JsonObject writeDepotInfo(DepotInfo depot) {
        JsonObject json = new JsonObject();
        json.addProperty("depotId", depot.getDepotId());
        json.addProperty("name", depot.getName());
        json.addProperty("transportMode", depot.getTransportMode());
        json.add("routeIds", writeLongArray(depot.getRouteIds()));
        json.add("departures", writeIntArray(depot.getDepartures()));
        json.addProperty("useRealTime", depot.isUseRealTime());
        json.addProperty("repeatInfinitely", depot.isRepeatInfinitely());
        json.addProperty("cruisingAltitude", depot.getCruisingAltitude());
        depot.getNextDepartureMillis().ifPresent(value -> json.addProperty("nextDepartureMillis", value));
        return json;
    }

    private static JsonObject writeFareAreaInfo(FareAreaInfo info) {
        JsonObject json = new JsonObject();
        json.addProperty("stationId", info.getStationId());
        json.addProperty("name", info.getName());
        json.addProperty("zone", info.getZone());
        if (info.getBounds() != null) {
            json.add("bounds", writeBounds(info.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(info.getInterchangeRouteIds()));
        return json;
    }

    private static JsonObject writeStationInfo(StationInfo info) {
        JsonObject json = new JsonObject();
        json.addProperty("dimension", info.getDimensionId());
        json.addProperty("stationId", info.getStationId());
        json.addProperty("name", info.getName());
        json.addProperty("zone", info.getZone());
        if (info.getBounds() != null) {
            json.add("bounds", writeBounds(info.getBounds()));
        }
        json.add("interchangeRouteIds", writeLongArray(info.getInterchangeRouteIds()));
        JsonArray platforms = new JsonArray();
        for (StationPlatformInfo platform : info.getPlatforms()) {
            platforms.add(writeStationPlatform(platform));
        }
        json.add("platforms", platforms);
        return json;
    }

    private static JsonObject writeStationPlatform(StationPlatformInfo platform) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", platform.getPlatformId());
        json.addProperty("name", platform.getPlatformName());
        json.add("routeIds", writeLongArray(platform.getRouteIds()));
        platform.getDepotId().ifPresent(id -> json.addProperty("depotId", id));
        return json;
    }

    private static JsonObject writeRouteNode(RouteNode node) {
        JsonObject json = writeNodeInfo(node.getNode());
        json.addProperty("segmentCategory", node.getSegmentCategory());
        json.addProperty("sequence", node.getSequence());
        return json;
    }

    private static JsonObject writeNodeInfo(NodeInfo node) {
        JsonObject json = new JsonObject();
        json.addProperty("x", node.getX());
        json.addProperty("y", node.getY());
        json.addProperty("z", node.getZ());
        json.addProperty("railType", node.getRailType());
        json.addProperty("platformSegment", node.isPlatformSegment());
        node.getStationId().ifPresent(id -> json.addProperty("stationId", id));
        return json;
    }

    private static JsonObject writePlatformTimetable(PlatformTimetable timetable) {
        JsonObject json = new JsonObject();
        json.addProperty("platformId", timetable.getPlatformId());
        JsonArray entries = new JsonArray();
        for (ScheduleEntry entry : timetable.getEntries()) {
            entries.add(writeScheduleEntry(entry));
        }
        json.add("entries", entries);
        return json;
    }

    private static JsonObject writeScheduleEntry(ScheduleEntry entry) {
        JsonObject json = new JsonObject();
        json.addProperty("routeId", entry.getRouteId());
        json.addProperty("arrivalMillis", entry.getArrivalMillis());
        json.addProperty("trainCars", entry.getTrainCars());
        json.addProperty("currentStationIndex", entry.getCurrentStationIndex());
        entry.getDelayMillis().ifPresent(delay -> json.addProperty("delayMillis", delay));
        return json;
    }

    private static JsonObject writeTrainStatus(TrainStatus status) {
        JsonObject json = new JsonObject();
        if (status.getTrainUuid() != null) {
            json.addProperty("trainUuid", status.getTrainUuid().toString());
        }
        json.addProperty("dimension", status.getDimensionId());
        json.addProperty("routeId", status.getRouteId());
        status.getDepotId().ifPresent(id -> json.addProperty("depotId", id));
        json.addProperty("transportMode", status.getTransportMode());
        status.getCurrentStationId().ifPresent(id -> json.addProperty("currentStationId", id));
        status.getNextStationId().ifPresent(id -> json.addProperty("nextStationId", id));
        status.getDelayMillis().ifPresent(delay -> json.addProperty("delayMillis", delay));
        json.addProperty("segmentCategory", status.getSegmentCategory());
        json.addProperty("progress", status.getProgress());
        status.getNode().ifPresent(node -> json.add("node", writeNodeInfo(node)));
        return json;
    }

    private static JsonObject writeBounds(Bounds bounds) {
        JsonObject json = new JsonObject();
        json.addProperty("minX", bounds.getMinX());
        json.addProperty("minY", bounds.getMinY());
        json.addProperty("minZ", bounds.getMinZ());
        json.addProperty("maxX", bounds.getMaxX());
        json.addProperty("maxY", bounds.getMaxY());
        json.addProperty("maxZ", bounds.getMaxZ());
        return json;
    }

    private static JsonArray writeLongArray(List<Long> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (Long value : values) {
                if (value != null) {
                    array.add(value);
                }
            }
        }
        return array;
    }

    private static JsonArray writeIntArray(List<Integer> values) {
        JsonArray array = new JsonArray();
        if (values != null) {
            for (Integer value : values) {
                if (value != null) {
                    array.add(value);
                }
            }
        }
        return array;
    }
}
