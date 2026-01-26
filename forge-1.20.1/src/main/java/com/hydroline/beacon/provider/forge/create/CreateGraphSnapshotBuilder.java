package com.hydroline.beacon.provider.forge.create;

import com.hydroline.beacon.provider.create.CreateEdgeIdFactory;
import com.hydroline.beacon.provider.create.CreateNetworkSnapshot;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class CreateGraphSnapshotBuilder {
    private static final double POLYLINE_STEP = 1.0d;
    private static final int MAX_POLYLINE_POINTS = 128;

    private CreateGraphSnapshotBuilder() {
    }

    static CreateNetworkSnapshot build(TrackGraph graph, long updatedAt) {
        if (graph == null || graph.id == null) {
            return new CreateNetworkSnapshot(Collections.<CreateNetworkSnapshot.GraphInfo>emptyList(),
                Collections.<CreateNetworkSnapshot.NodeInfo>emptyList(),
                Collections.<CreateNetworkSnapshot.EdgeInfo>emptyList(),
                Collections.<CreateNetworkSnapshot.EdgePolylinePoint>emptyList(),
                Collections.<CreateNetworkSnapshot.StationInfo>emptyList(),
                Collections.<CreateNetworkSnapshot.SignalBoundaryInfo>emptyList(),
                Collections.<CreateNetworkSnapshot.EdgeSegmentInfo>emptyList());
        }
        String graphId = graph.id.toString();
        int checksum = graph.getChecksum();
        int color = extractColor(graph);
        CreateNetworkSnapshot.GraphInfo graphInfo = new CreateNetworkSnapshot.GraphInfo(graphId, checksum, color, updatedAt);

        List<CreateNetworkSnapshot.NodeInfo> nodes = new ArrayList<CreateNetworkSnapshot.NodeInfo>();
        List<TrackNode> resolvedNodes = new ArrayList<TrackNode>();
        for (TrackNodeLocation location : graph.getNodes()) {
            TrackNode node = graph.locateNode(location);
            if (node == null) {
                continue;
            }
            resolvedNodes.add(node);
            Vec3 position = location.getLocation();
            Vec3 normal = node.getNormal();
            String dimension = resolveDimension(location.getDimension());
            nodes.add(new CreateNetworkSnapshot.NodeInfo(
                graphId,
                node.getNetId(),
                dimension,
                position.x,
                position.y,
                position.z,
                normal == null ? 0 : normal.x,
                normal == null ? 0 : normal.y,
                normal == null ? 0 : normal.z,
                location.yOffsetPixels
            ));
        }

        Set<TrackEdge> visited = Collections.newSetFromMap(new IdentityHashMap<TrackEdge, Boolean>());
        List<CreateNetworkSnapshot.EdgeInfo> edges = new ArrayList<CreateNetworkSnapshot.EdgeInfo>();
        List<CreateNetworkSnapshot.EdgePolylinePoint> polylines = new ArrayList<CreateNetworkSnapshot.EdgePolylinePoint>();
        List<CreateNetworkSnapshot.StationInfo> stations = new ArrayList<CreateNetworkSnapshot.StationInfo>();
        List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries = new ArrayList<CreateNetworkSnapshot.SignalBoundaryInfo>();
        List<CreateNetworkSnapshot.EdgeSegmentInfo> segments = new ArrayList<CreateNetworkSnapshot.EdgeSegmentInfo>();

        for (TrackNode node : resolvedNodes) {
            Map<TrackNode, TrackEdge> connections = graph.getConnectionsFrom(node);
            if (connections == null) {
                continue;
            }
            for (TrackEdge edge : connections.values()) {
                if (edge == null || !visited.add(edge)) {
                    continue;
                }
                TrackNode node1 = edge.node1;
                TrackNode node2 = edge.node2;
                if (node1 == null || node2 == null) {
                    continue;
                }
                String materialId = edge.getTrackMaterial() == null ? null : edge.getTrackMaterial().id.toString();
                String edgeId = CreateEdgeIdFactory.build(
                    graphId,
                    node1.getNetId(),
                    node2.getNetId(),
                    edge.isTurn(),
                    edge.isInterDimensional(),
                    edge.getLength(),
                    materialId
                );
                edges.add(new CreateNetworkSnapshot.EdgeInfo(
                    edgeId,
                    graphId,
                    node1.getNetId(),
                    node2.getNetId(),
                    edge.isTurn(),
                    edge.isInterDimensional(),
                    edge.getLength(),
                    materialId
                ));
                appendPolyline(graph, edge, edgeId, polylines);
                EdgeData edgeData = edge.getEdgeData();
                if (edgeData != null && edgeData.hasPoints()) {
                    appendEdgePoints(graph, edge, edgeId, edgeData.getPoints(), stations, boundaries);
                }
                appendSegments(graph, edge, edgeId, edgeData, segments);
            }
        }

        return new CreateNetworkSnapshot(
            Collections.singletonList(graphInfo),
            nodes,
            edges,
            polylines,
            stations,
            boundaries,
            segments
        );
    }

    private static void appendPolyline(TrackGraph graph, TrackEdge edge, String edgeId,
                                       List<CreateNetworkSnapshot.EdgePolylinePoint> output) {
        if (edge == null || edge.isInterDimensional()) {
            return;
        }
        double length = edge.getLength();
        if (length <= 0) {
            return;
        }
        int steps = Math.max(2, (int) Math.ceil(length / POLYLINE_STEP) + 1);
        steps = Math.min(steps, MAX_POLYLINE_POINTS);
        for (int i = 0; i < steps; i++) {
            double t = steps == 1 ? 0 : (double) i / (double) (steps - 1);
            Vec3 pos = edge.getPosition(graph, t);
            output.add(new CreateNetworkSnapshot.EdgePolylinePoint(edgeId, i, pos.x, pos.y, pos.z));
        }
    }

    private static void appendEdgePoints(TrackGraph graph,
                                         TrackEdge edge,
                                         String edgeId,
                                         List<TrackEdgePoint> points,
                                         List<CreateNetworkSnapshot.StationInfo> stations,
                                         List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries) {
        if (points == null || points.isEmpty()) {
            return;
        }
        String graphId = graph.id.toString();
        String dimension = resolveDimension(edge.node1.getLocation().getDimension());
        double length = edge.getLength();
        for (TrackEdgePoint point : points) {
            if (point instanceof GlobalStation) {
                GlobalStation station = (GlobalStation) point;
                double position = point.getLocationOn(edge);
                Vec3 pos = resolvePointPosition(graph, edge, position, length);
                stations.add(new CreateNetworkSnapshot.StationInfo(
                    station.getId().toString(),
                    graphId,
                    edgeId,
                    position,
                    station.name,
                    dimension,
                    pos.x,
                    pos.y,
                    pos.z
                ));
            } else if (point instanceof SignalBoundary) {
                SignalBoundary boundary = (SignalBoundary) point;
                double position = point.getLocationOn(edge);
                Vec3 pos = resolvePointPosition(graph, edge, position, length);
                String groupPrimary = boundary.getGroup(edge.node1) == null ? null : boundary.getGroup(edge.node1).toString();
                String groupSecondary = boundary.getGroup(edge.node2) == null ? null : boundary.getGroup(edge.node2).toString();
                boundaries.add(new CreateNetworkSnapshot.SignalBoundaryInfo(
                    boundary.getId().toString(),
                    graphId,
                    edgeId,
                    position,
                    groupPrimary,
                    groupSecondary,
                    dimension,
                    pos.x,
                    pos.y,
                    pos.z
                ));
            }
        }
    }

    private static void appendSegments(TrackGraph graph,
                                       TrackEdge edge,
                                       String edgeId,
                                       EdgeData edgeData,
                                       List<CreateNetworkSnapshot.EdgeSegmentInfo> segments) {
        if (edge == null || edgeData == null) {
            return;
        }
        double length = edge.getLength();
        List<Double> boundaries = new ArrayList<Double>();
        if (edgeData.hasPoints()) {
            for (TrackEdgePoint point : edgeData.getPoints()) {
                if (point instanceof SignalBoundary) {
                    boundaries.add(point.getLocationOn(edge));
                }
            }
        }
        boundaries = boundaries.stream().sorted().collect(Collectors.toList());
        double start = 0.0d;
        int index = 0;
        if (boundaries.isEmpty()) {
            addSegment(graph, edgeData, edgeId, start, length, index++, segments);
            return;
        }
        for (double boundary : boundaries) {
            double end = Math.max(boundary, start);
            addSegment(graph, edgeData, edgeId, start, end, index++, segments);
            start = end;
        }
        if (length > start) {
            addSegment(graph, edgeData, edgeId, start, length, index, segments);
        }
    }

    private static void addSegment(TrackGraph graph,
                                   EdgeData edgeData,
                                   String edgeId,
                                   double start,
                                   double end,
                                   int index,
                                   List<CreateNetworkSnapshot.EdgeSegmentInfo> segments) {
        double mid = (start + end) * 0.5d;
        UUID group = edgeData.getGroupAtPosition(graph, mid);
        String groupId = group == null ? null : group.toString();
        segments.add(new CreateNetworkSnapshot.EdgeSegmentInfo(edgeId + ":" + index, edgeId, start, end, groupId));
    }

    private static Vec3 resolvePointPosition(TrackGraph graph, TrackEdge edge, double position, double length) {
        if (length <= 0) {
            return edge.node1.getLocation().getLocation();
        }
        double t = position / length;
        t = Math.max(0.0d, Math.min(1.0d, t));
        return edge.getPosition(graph, t);
    }

    private static String resolveDimension(ResourceKey<Level> key) {
        return key == null ? "" : key.location().toString();
    }

    private static int extractColor(TrackGraph graph) {
        try {
            Field colorField = TrackGraph.class.getField("color");
            Object colorValue = colorField.get(graph);
            if (colorValue == null) {
                return 0;
            }
            Integer result = invokeColorMethod(colorValue, "getAsInt");
            if (result != null) {
                return result.intValue();
            }
            result = invokeColorMethod(colorValue, "getRGB");
            if (result != null) {
                return result.intValue();
            }
            result = invokeColorMethod(colorValue, "getValue");
            if (result != null) {
                return result.intValue();
            }
            return colorValue.hashCode();
        } catch (Exception ex) {
            return 0;
        }
    }

    private static Integer invokeColorMethod(Object colorValue, String methodName) {
        try {
            Method method = colorValue.getClass().getMethod(methodName);
            Object value = method.invoke(colorValue);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
