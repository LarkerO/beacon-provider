package com.hydroline.beacon.provider.create;

import java.util.Collections;
import java.util.List;

public final class CreateNetworkSnapshot {
    private final List<GraphInfo> graphs;
    private final List<NodeInfo> nodes;
    private final List<EdgeInfo> edges;
    private final List<EdgePolylinePoint> edgePolylines;
    private final List<StationInfo> stations;
    private final List<SignalBoundaryInfo> signalBoundaries;
    private final List<EdgeSegmentInfo> edgeSegments;

    public CreateNetworkSnapshot(List<GraphInfo> graphs,
                                 List<NodeInfo> nodes,
                                 List<EdgeInfo> edges,
                                 List<EdgePolylinePoint> edgePolylines,
                                 List<StationInfo> stations,
                                 List<SignalBoundaryInfo> signalBoundaries,
                                 List<EdgeSegmentInfo> edgeSegments) {
        this.graphs = graphs == null ? Collections.emptyList() : graphs;
        this.nodes = nodes == null ? Collections.emptyList() : nodes;
        this.edges = edges == null ? Collections.emptyList() : edges;
        this.edgePolylines = edgePolylines == null ? Collections.emptyList() : edgePolylines;
        this.stations = stations == null ? Collections.emptyList() : stations;
        this.signalBoundaries = signalBoundaries == null ? Collections.emptyList() : signalBoundaries;
        this.edgeSegments = edgeSegments == null ? Collections.emptyList() : edgeSegments;
    }

    public List<GraphInfo> getGraphs() {
        return graphs;
    }

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public List<EdgeInfo> getEdges() {
        return edges;
    }

    public List<EdgePolylinePoint> getEdgePolylines() {
        return edgePolylines;
    }

    public List<StationInfo> getStations() {
        return stations;
    }

    public List<SignalBoundaryInfo> getSignalBoundaries() {
        return signalBoundaries;
    }

    public List<EdgeSegmentInfo> getEdgeSegments() {
        return edgeSegments;
    }

    public static final class GraphInfo {
        private final String graphId;
        private final int checksum;
        private final int color;
        private final long updatedAt;

        public GraphInfo(String graphId, int checksum, int color, long updatedAt) {
            this.graphId = graphId;
            this.checksum = checksum;
            this.color = color;
            this.updatedAt = updatedAt;
        }

        public String getGraphId() {
            return graphId;
        }

        public int getChecksum() {
            return checksum;
        }

        public int getColor() {
            return color;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }

    public static final class NodeInfo {
        private final String graphId;
        private final int netId;
        private final String dimension;
        private final double x;
        private final double y;
        private final double z;
        private final double normalX;
        private final double normalY;
        private final double normalZ;
        private final int yOffsetPixels;

        public NodeInfo(String graphId, int netId, String dimension,
                        double x, double y, double z,
                        double normalX, double normalY, double normalZ,
                        int yOffsetPixels) {
            this.graphId = graphId;
            this.netId = netId;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
            this.yOffsetPixels = yOffsetPixels;
        }

        public String getGraphId() {
            return graphId;
        }

        public int getNetId() {
            return netId;
        }

        public String getDimension() {
            return dimension;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }

        public double getNormalX() {
            return normalX;
        }

        public double getNormalY() {
            return normalY;
        }

        public double getNormalZ() {
            return normalZ;
        }

        public int getYOffsetPixels() {
            return yOffsetPixels;
        }
    }

    public static final class EdgeInfo {
        private final String edgeId;
        private final String graphId;
        private final int node1NetId;
        private final int node2NetId;
        private final boolean turn;
        private final boolean portal;
        private final double length;
        private final String materialId;

        public EdgeInfo(String edgeId, String graphId, int node1NetId, int node2NetId,
                        boolean turn, boolean portal, double length, String materialId) {
            this.edgeId = edgeId;
            this.graphId = graphId;
            this.node1NetId = node1NetId;
            this.node2NetId = node2NetId;
            this.turn = turn;
            this.portal = portal;
            this.length = length;
            this.materialId = materialId;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public String getGraphId() {
            return graphId;
        }

        public int getNode1NetId() {
            return node1NetId;
        }

        public int getNode2NetId() {
            return node2NetId;
        }

        public boolean isTurn() {
            return turn;
        }

        public boolean isPortal() {
            return portal;
        }

        public double getLength() {
            return length;
        }

        public String getMaterialId() {
            return materialId;
        }
    }

    public static final class EdgePolylinePoint {
        private final String edgeId;
        private final int seq;
        private final double x;
        private final double y;
        private final double z;

        public EdgePolylinePoint(String edgeId, int seq, double x, double y, double z) {
            this.edgeId = edgeId;
            this.seq = seq;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public int getSeq() {
            return seq;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }
    }

    public static final class StationInfo {
        private final String stationId;
        private final String graphId;
        private final String edgeId;
        private final double position;
        private final String name;
        private final String dimension;
        private final double x;
        private final double y;
        private final double z;

        public StationInfo(String stationId, String graphId, String edgeId,
                           double position, String name, String dimension,
                           double x, double y, double z) {
            this.stationId = stationId;
            this.graphId = graphId;
            this.edgeId = edgeId;
            this.position = position;
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getStationId() {
            return stationId;
        }

        public String getGraphId() {
            return graphId;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public double getPosition() {
            return position;
        }

        public String getName() {
            return name;
        }

        public String getDimension() {
            return dimension;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }
    }

    public static final class SignalBoundaryInfo {
        private final String boundaryId;
        private final String graphId;
        private final String edgeId;
        private final double position;
        private final String groupIdPrimary;
        private final String groupIdSecondary;
        private final String dimension;
        private final double x;
        private final double y;
        private final double z;

        public SignalBoundaryInfo(String boundaryId, String graphId, String edgeId,
                                  double position, String groupIdPrimary, String groupIdSecondary,
                                  String dimension, double x, double y, double z) {
            this.boundaryId = boundaryId;
            this.graphId = graphId;
            this.edgeId = edgeId;
            this.position = position;
            this.groupIdPrimary = groupIdPrimary;
            this.groupIdSecondary = groupIdSecondary;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getBoundaryId() {
            return boundaryId;
        }

        public String getGraphId() {
            return graphId;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public double getPosition() {
            return position;
        }

        public String getGroupIdPrimary() {
            return groupIdPrimary;
        }

        public String getGroupIdSecondary() {
            return groupIdSecondary;
        }

        public String getDimension() {
            return dimension;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getZ() {
            return z;
        }
    }

    public static final class EdgeSegmentInfo {
        private final String segmentId;
        private final String edgeId;
        private final double startPos;
        private final double endPos;
        private final String groupId;

        public EdgeSegmentInfo(String segmentId, String edgeId, double startPos, double endPos, String groupId) {
            this.segmentId = segmentId;
            this.edgeId = edgeId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.groupId = groupId;
        }

        public String getSegmentId() {
            return segmentId;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public double getStartPos() {
            return startPos;
        }

        public double getEndPos() {
            return endPos;
        }

        public String getGroupId() {
            return groupId;
        }
    }
}
