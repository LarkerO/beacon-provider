package com.hydroline.beacon.provider.create;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CreateJsonWriter {
    private CreateJsonWriter() {
    }

    public static JsonObject writeNetworkSnapshot(CreateNetworkSnapshot snapshot, boolean includePolylines) {
        JsonObject payload = new JsonObject();
        payload.addProperty("timestamp", System.currentTimeMillis());
        payload.add("graphs", writeGraphs(snapshot.getGraphs()));
        payload.add("nodes", writeNodes(snapshot.getNodes()));
        payload.add("edges", writeEdges(snapshot.getEdges()));
        if (includePolylines) {
            payload.add("edgePolylines", writeEdgePolylines(snapshot.getEdgePolylines()));
        }
        payload.add("stations", writeStations(snapshot.getStations()));
        payload.add("signalBoundaries", writeSignalBoundaries(snapshot.getSignalBoundaries()));
        payload.add("edgeSegments", writeEdgeSegments(snapshot.getEdgeSegments()));
        return payload;
    }

    public static JsonObject writeRealtimeSnapshot(CreateRealtimeSnapshot snapshot) {
        JsonObject payload = new JsonObject();
        payload.addProperty("timestamp", snapshot.getCapturedAt());
        payload.add("trains", writeTrains(snapshot.getTrains()));
        payload.add("groups", writeGroups(snapshot.getGroups()));
        return payload;
    }

    private static JsonArray writeGraphs(List<CreateNetworkSnapshot.GraphInfo> graphs) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.GraphInfo graph : graphs) {
            JsonObject json = new JsonObject();
            json.addProperty("graphId", graph.getGraphId());
            json.addProperty("checksum", graph.getChecksum());
            json.addProperty("color", graph.getColor());
            json.addProperty("updatedAt", graph.getUpdatedAt());
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeNodes(List<CreateNetworkSnapshot.NodeInfo> nodes) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.NodeInfo node : nodes) {
            JsonObject json = new JsonObject();
            json.addProperty("graphId", node.getGraphId());
            json.addProperty("netId", node.getNetId());
            json.addProperty("dimension", node.getDimension());
            json.addProperty("x", node.getX());
            json.addProperty("y", node.getY());
            json.addProperty("z", node.getZ());
            JsonArray normal = new JsonArray();
            normal.add(node.getNormalX());
            normal.add(node.getNormalY());
            normal.add(node.getNormalZ());
            json.add("normal", normal);
            json.addProperty("yOffsetPixels", node.getYOffsetPixels());
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeEdges(List<CreateNetworkSnapshot.EdgeInfo> edges) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.EdgeInfo edge : edges) {
            JsonObject json = new JsonObject();
            json.addProperty("edgeId", edge.getEdgeId());
            json.addProperty("graphId", edge.getGraphId());
            json.addProperty("node1NetId", edge.getNode1NetId());
            json.addProperty("node2NetId", edge.getNode2NetId());
            json.addProperty("isTurn", edge.isTurn());
            json.addProperty("isPortal", edge.isPortal());
            json.addProperty("length", edge.getLength());
            if (edge.getMaterialId() != null) {
                json.addProperty("materialId", edge.getMaterialId());
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeEdgePolylines(List<CreateNetworkSnapshot.EdgePolylinePoint> points) {
        Map<String, List<CreateNetworkSnapshot.EdgePolylinePoint>> grouped = new LinkedHashMap<String, List<CreateNetworkSnapshot.EdgePolylinePoint>>();
        for (CreateNetworkSnapshot.EdgePolylinePoint point : points) {
            List<CreateNetworkSnapshot.EdgePolylinePoint> bucket = grouped.get(point.getEdgeId());
            if (bucket == null) {
                bucket = new ArrayList<CreateNetworkSnapshot.EdgePolylinePoint>();
                grouped.put(point.getEdgeId(), bucket);
            }
            bucket.add(point);
        }
        JsonArray array = new JsonArray();
        for (Map.Entry<String, List<CreateNetworkSnapshot.EdgePolylinePoint>> entry : grouped.entrySet()) {
            JsonObject json = new JsonObject();
            json.addProperty("edgeId", entry.getKey());
            JsonArray path = new JsonArray();
            for (CreateNetworkSnapshot.EdgePolylinePoint point : entry.getValue()) {
                JsonArray coords = new JsonArray();
                coords.add(point.getX());
                coords.add(point.getY());
                coords.add(point.getZ());
                path.add(coords);
            }
            json.add("points", path);
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeStations(List<CreateNetworkSnapshot.StationInfo> stations) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.StationInfo station : stations) {
            JsonObject json = new JsonObject();
            json.addProperty("stationId", station.getStationId());
            json.addProperty("graphId", station.getGraphId());
            json.addProperty("edgeId", station.getEdgeId());
            json.addProperty("position", station.getPosition());
            if (station.getName() != null) {
                json.addProperty("name", station.getName());
            }
            json.addProperty("dimension", station.getDimension());
            json.addProperty("x", station.getX());
            json.addProperty("y", station.getY());
            json.addProperty("z", station.getZ());
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeSignalBoundaries(List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.SignalBoundaryInfo boundary : boundaries) {
            JsonObject json = new JsonObject();
            json.addProperty("boundaryId", boundary.getBoundaryId());
            json.addProperty("graphId", boundary.getGraphId());
            json.addProperty("edgeId", boundary.getEdgeId());
            json.addProperty("position", boundary.getPosition());
            if (boundary.getGroupIdPrimary() != null) {
                json.addProperty("groupIdPrimary", boundary.getGroupIdPrimary());
            }
            if (boundary.getGroupIdSecondary() != null) {
                json.addProperty("groupIdSecondary", boundary.getGroupIdSecondary());
            }
            json.addProperty("dimension", boundary.getDimension());
            json.addProperty("x", boundary.getX());
            json.addProperty("y", boundary.getY());
            json.addProperty("z", boundary.getZ());
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeEdgeSegments(List<CreateNetworkSnapshot.EdgeSegmentInfo> segments) {
        JsonArray array = new JsonArray();
        for (CreateNetworkSnapshot.EdgeSegmentInfo segment : segments) {
            JsonObject json = new JsonObject();
            json.addProperty("segmentId", segment.getSegmentId());
            json.addProperty("edgeId", segment.getEdgeId());
            json.addProperty("startPos", segment.getStartPos());
            json.addProperty("endPos", segment.getEndPos());
            if (segment.getGroupId() != null) {
                json.addProperty("groupId", segment.getGroupId());
            }
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeTrains(List<CreateRealtimeSnapshot.TrainStatus> trains) {
        JsonArray array = new JsonArray();
        for (CreateRealtimeSnapshot.TrainStatus train : trains) {
            JsonObject json = new JsonObject();
            json.addProperty("trainId", train.getTrainId());
            json.addProperty("name", train.getName());
            if (train.getIconId() != null) {
                json.addProperty("iconId", train.getIconId());
            }
            json.addProperty("mapColorIndex", train.getMapColorIndex());
            if (train.getStatus() != null) {
                json.addProperty("status", train.getStatus());
            }
            json.addProperty("speed", train.getSpeed());
            json.addProperty("targetSpeed", train.getTargetSpeed());
            json.addProperty("throttle", train.getThrottle());
            json.addProperty("derailed", train.isDerailed());
            if (train.getGraphId() != null) {
                json.addProperty("graphId", train.getGraphId());
            }
            if (train.getCurrentStationId() != null) {
                json.addProperty("currentStationId", train.getCurrentStationId());
            }
            if (train.getScheduleTitle() != null) {
                json.addProperty("scheduleTitle", train.getScheduleTitle());
            }
            if (train.getScheduleEntry() != null) {
                json.addProperty("scheduleEntry", train.getScheduleEntry());
            }
            if (train.getScheduleState() != null) {
                json.addProperty("scheduleState", train.getScheduleState());
            }
            json.addProperty("schedulePaused", train.isSchedulePaused());
            json.addProperty("scheduleCompleted", train.isScheduleCompleted());
            json.addProperty("scheduleAuto", train.isScheduleAuto());
            json.add("positions", writeTrainPositions(train.getPositions()));
            json.add("carriages", writeCarriages(train.getCarriages()));
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeTrainPositions(List<CreateRealtimeSnapshot.DimensionPosition> positions) {
        JsonArray array = new JsonArray();
        for (CreateRealtimeSnapshot.DimensionPosition position : positions) {
            JsonObject json = new JsonObject();
            json.addProperty("dimension", position.getDimension());
            json.addProperty("x", position.getX());
            json.addProperty("y", position.getY());
            json.addProperty("z", position.getZ());
            array.add(json);
        }
        return array;
    }

    private static JsonArray writeCarriages(List<CreateRealtimeSnapshot.CarriageInfo> carriages) {
        JsonArray array = new JsonArray();
        for (CreateRealtimeSnapshot.CarriageInfo carriage : carriages) {
            JsonObject json = new JsonObject();
            json.addProperty("id", carriage.getId());
            json.addProperty("bogeySpacing", carriage.getBogeySpacing());
            if (carriage.getLeading() != null) {
                json.add("leading", writeTravellingPoint(carriage.getLeading()));
            }
            if (carriage.getTrailing() != null) {
                json.add("trailing", writeTravellingPoint(carriage.getTrailing()));
            }
            if (carriage.getLeadingBogey() != null) {
                json.add("leadingBogey", writeBogey(carriage.getLeadingBogey()));
            }
            if (carriage.getTrailingBogey() != null) {
                json.add("trailingBogey", writeBogey(carriage.getTrailingBogey()));
            }
            array.add(json);
        }
        return array;
    }

    private static JsonObject writeTravellingPoint(CreateRealtimeSnapshot.TravellingPointInfo point) {
        JsonObject json = new JsonObject();
        if (point.getEdgeId() != null) {
            json.addProperty("edgeId", point.getEdgeId());
        }
        json.addProperty("node1NetId", point.getNode1NetId());
        json.addProperty("node2NetId", point.getNode2NetId());
        json.addProperty("position", point.getPosition());
        if (point.getDimension() != null) {
            json.addProperty("dimension", point.getDimension());
        }
        json.addProperty("x", point.getX());
        json.addProperty("y", point.getY());
        json.addProperty("z", point.getZ());
        return json;
    }

    private static JsonObject writeBogey(CreateRealtimeSnapshot.BogeyInfo bogey) {
        JsonObject json = new JsonObject();
        if (bogey.getStyleId() != null) {
            json.addProperty("styleId", bogey.getStyleId());
        }
        if (bogey.getSize() != null) {
            json.addProperty("size", bogey.getSize());
        }
        json.addProperty("upsideDown", bogey.isUpsideDown());
        return json;
    }

    private static JsonArray writeGroups(List<CreateRealtimeSnapshot.GroupStatus> groups) {
        JsonArray array = new JsonArray();
        for (CreateRealtimeSnapshot.GroupStatus group : groups) {
            JsonObject json = new JsonObject();
            json.addProperty("groupId", group.getGroupId());
            if (group.getColor() != null) {
                json.addProperty("color", group.getColor());
            }
            if (group.getReservedBoundaryId() != null) {
                json.addProperty("reservedBoundaryId", group.getReservedBoundaryId());
            }
            JsonArray trains = new JsonArray();
            for (String trainId : group.getTrainIds()) {
                trains.add(trainId);
            }
            json.add("trainIds", trains);
            array.add(json);
        }
        return array;
    }
}
