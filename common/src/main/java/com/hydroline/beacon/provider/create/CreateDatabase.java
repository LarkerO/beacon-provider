package com.hydroline.beacon.provider.create;

import com.hydroline.beacon.provider.BeaconProviderMod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CreateDatabase implements AutoCloseable {
    private final Path dbPath;
    private final Object lock = new Object();

    public CreateDatabase(Path dbPath) {
        this.dbPath = dbPath;
    }

    public void initialize() throws SQLException {
        synchronized (lock) {
            try {
                if (dbPath.getParent() != null) {
                    Files.createDirectories(dbPath.getParent());
                }
            } catch (Exception ex) {
                throw new SQLException("Failed to create database directory", ex);
            }
            try (Connection connection = openConnection();
                 Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA foreign_keys=ON");
                stmt.execute("PRAGMA busy_timeout=5000");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_graphs (graph_id TEXT PRIMARY KEY, checksum INTEGER NOT NULL, color INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_nodes (graph_id TEXT NOT NULL, node_net_id INTEGER NOT NULL, dimension TEXT NOT NULL, x REAL, y REAL, z REAL, normal_x REAL, normal_y REAL, normal_z REAL, y_offset_pixels INTEGER, PRIMARY KEY(graph_id, node_net_id))");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_edges (edge_id TEXT PRIMARY KEY, graph_id TEXT NOT NULL, node1_net_id INTEGER NOT NULL, node2_net_id INTEGER NOT NULL, is_turn INTEGER NOT NULL, is_portal INTEGER NOT NULL, length REAL NOT NULL, material_id TEXT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_edge_polyline (edge_id TEXT NOT NULL, seq INTEGER NOT NULL, x REAL, y REAL, z REAL, PRIMARY KEY(edge_id, seq))");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_stations (station_id TEXT PRIMARY KEY, graph_id TEXT NOT NULL, edge_id TEXT NOT NULL, position REAL, name TEXT, dimension TEXT, x REAL, y REAL, z REAL)");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_signal_boundaries (boundary_id TEXT PRIMARY KEY, graph_id TEXT NOT NULL, edge_id TEXT NOT NULL, position REAL, group_id_primary TEXT, group_id_secondary TEXT, dimension TEXT, x REAL, y REAL, z REAL)");
                stmt.execute("CREATE TABLE IF NOT EXISTS create_edge_segments (segment_id TEXT PRIMARY KEY, edge_id TEXT NOT NULL, start_pos REAL, end_pos REAL, group_id TEXT)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_nodes_graph ON create_nodes(graph_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_edges_graph ON create_edges(graph_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_stations_graph ON create_stations(graph_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_boundaries_graph ON create_signal_boundaries(graph_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_polyline_edge ON create_edge_polyline(edge_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_create_segments_edge ON create_edge_segments(edge_id)");
            }
        }
    }

    public Map<String, Integer> loadGraphChecksums() {
        synchronized (lock) {
            try (Connection connection = openConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT graph_id, checksum FROM create_graphs")) {
                ResultSet rs = stmt.executeQuery();
                Map<String, Integer> result = new HashMap<String, Integer>();
                while (rs.next()) {
                    result.put(rs.getString("graph_id"), rs.getInt("checksum"));
                }
                return result;
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to load Create graph checksums", ex);
                return Collections.emptyMap();
            }
        }
    }

    public Set<String> loadGraphIds() {
        synchronized (lock) {
            try (Connection connection = openConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT graph_id FROM create_graphs")) {
                ResultSet rs = stmt.executeQuery();
                Set<String> result = new HashSet<String>();
                while (rs.next()) {
                    result.add(rs.getString("graph_id"));
                }
                return result;
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to load Create graph ids", ex);
                return Collections.emptySet();
            }
        }
    }

    public boolean hasGraph(String graphId) {
        synchronized (lock) {
            try (Connection connection = openConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT 1 FROM create_graphs WHERE graph_id = ?")) {
                stmt.setString(1, graphId);
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to check Create graph id", ex);
                return false;
            }
        }
    }

    public void upsertGraph(CreateNetworkSnapshot snapshot) {
        if (snapshot == null || snapshot.getGraphs().isEmpty()) {
            return;
        }
        CreateNetworkSnapshot.GraphInfo graph = snapshot.getGraphs().get(0);
        String graphId = graph.getGraphId();
        synchronized (lock) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                deleteGraphInternal(connection, graphId);
                insertGraph(connection, graph);
                insertNodes(connection, snapshot.getNodes());
                insertEdges(connection, snapshot.getEdges());
                insertPolyline(connection, snapshot.getEdgePolylines());
                insertStations(connection, snapshot.getStations());
                insertSignalBoundaries(connection, snapshot.getSignalBoundaries());
                insertEdgeSegments(connection, snapshot.getEdgeSegments());
                connection.commit();
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to upsert Create graph {}", graphId, ex);
            }
        }
    }

    public void deleteGraph(String graphId) {
        synchronized (lock) {
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(false);
                deleteGraphInternal(connection, graphId);
                connection.commit();
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to delete Create graph {}", graphId, ex);
            }
        }
    }

    public CreateNetworkSnapshot queryNetworkSnapshot(String graphId, boolean includePolylines) {
        synchronized (lock) {
            try (Connection connection = openConnection()) {
                List<CreateNetworkSnapshot.GraphInfo> graphs = queryGraphs(connection, graphId);
                List<CreateNetworkSnapshot.NodeInfo> nodes = queryNodes(connection, graphId);
                List<CreateNetworkSnapshot.EdgeInfo> edges = queryEdges(connection, graphId);
                List<CreateNetworkSnapshot.EdgePolylinePoint> polylines = includePolylines
                    ? queryPolylines(connection, graphId)
                    : Collections.<CreateNetworkSnapshot.EdgePolylinePoint>emptyList();
                List<CreateNetworkSnapshot.StationInfo> stations = queryStations(connection, graphId);
                List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries = querySignalBoundaries(connection, graphId);
                List<CreateNetworkSnapshot.EdgeSegmentInfo> segments = queryEdgeSegments(connection, graphId);
                return new CreateNetworkSnapshot(graphs, nodes, edges, polylines, stations, boundaries, segments);
            } catch (SQLException ex) {
                BeaconProviderMod.LOGGER.warn("Failed to query Create network snapshot", ex);
                return new CreateNetworkSnapshot(Collections.<CreateNetworkSnapshot.GraphInfo>emptyList(),
                    Collections.<CreateNetworkSnapshot.NodeInfo>emptyList(),
                    Collections.<CreateNetworkSnapshot.EdgeInfo>emptyList(),
                    Collections.<CreateNetworkSnapshot.EdgePolylinePoint>emptyList(),
                    Collections.<CreateNetworkSnapshot.StationInfo>emptyList(),
                    Collections.<CreateNetworkSnapshot.SignalBoundaryInfo>emptyList(),
                    Collections.<CreateNetworkSnapshot.EdgeSegmentInfo>emptyList());
            }
        }
    }

    @Override
    public void close() {
        // connections are short-lived; nothing to close
    }

    private Connection openConnection() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private void deleteGraphInternal(Connection connection, String graphId) throws SQLException {
        List<String> edgeIds = new ArrayList<String>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT edge_id FROM create_edges WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                edgeIds.add(rs.getString("edge_id"));
            }
        }
        if (!edgeIds.isEmpty()) {
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_edge_polyline WHERE edge_id = ?")) {
                for (String edgeId : edgeIds) {
                    stmt.setString(1, edgeId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_edge_segments WHERE edge_id = ?")) {
                for (String edgeId : edgeIds) {
                    stmt.setString(1, edgeId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_edges WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_nodes WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_stations WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_signal_boundaries WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            stmt.executeUpdate();
        }
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM create_graphs WHERE graph_id = ?")) {
            stmt.setString(1, graphId);
            stmt.executeUpdate();
        }
    }

    private void insertGraph(Connection connection, CreateNetworkSnapshot.GraphInfo graph) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO create_graphs (graph_id, checksum, color, updated_at) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, graph.getGraphId());
            stmt.setInt(2, graph.getChecksum());
            stmt.setInt(3, graph.getColor());
            stmt.setLong(4, graph.getUpdatedAt());
            stmt.executeUpdate();
        }
    }

    private void insertNodes(Connection connection, List<CreateNetworkSnapshot.NodeInfo> nodes) throws SQLException {
        if (nodes.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_nodes (graph_id, node_net_id, dimension, x, y, z, normal_x, normal_y, normal_z, y_offset_pixels) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.NodeInfo node : nodes) {
                stmt.setString(1, node.getGraphId());
                stmt.setInt(2, node.getNetId());
                stmt.setString(3, node.getDimension());
                stmt.setDouble(4, node.getX());
                stmt.setDouble(5, node.getY());
                stmt.setDouble(6, node.getZ());
                stmt.setDouble(7, node.getNormalX());
                stmt.setDouble(8, node.getNormalY());
                stmt.setDouble(9, node.getNormalZ());
                stmt.setInt(10, node.getYOffsetPixels());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertEdges(Connection connection, List<CreateNetworkSnapshot.EdgeInfo> edges) throws SQLException {
        if (edges.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_edges (edge_id, graph_id, node1_net_id, node2_net_id, is_turn, is_portal, length, material_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.EdgeInfo edge : edges) {
                stmt.setString(1, edge.getEdgeId());
                stmt.setString(2, edge.getGraphId());
                stmt.setInt(3, edge.getNode1NetId());
                stmt.setInt(4, edge.getNode2NetId());
                stmt.setInt(5, edge.isTurn() ? 1 : 0);
                stmt.setInt(6, edge.isPortal() ? 1 : 0);
                stmt.setDouble(7, edge.getLength());
                stmt.setString(8, edge.getMaterialId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertPolyline(Connection connection, List<CreateNetworkSnapshot.EdgePolylinePoint> points) throws SQLException {
        if (points.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_edge_polyline (edge_id, seq, x, y, z) VALUES (?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.EdgePolylinePoint point : points) {
                stmt.setString(1, point.getEdgeId());
                stmt.setInt(2, point.getSeq());
                stmt.setDouble(3, point.getX());
                stmt.setDouble(4, point.getY());
                stmt.setDouble(5, point.getZ());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertStations(Connection connection, List<CreateNetworkSnapshot.StationInfo> stations) throws SQLException {
        if (stations.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_stations (station_id, graph_id, edge_id, position, name, dimension, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.StationInfo station : stations) {
                stmt.setString(1, station.getStationId());
                stmt.setString(2, station.getGraphId());
                stmt.setString(3, station.getEdgeId());
                stmt.setDouble(4, station.getPosition());
                stmt.setString(5, station.getName());
                stmt.setString(6, station.getDimension());
                stmt.setDouble(7, station.getX());
                stmt.setDouble(8, station.getY());
                stmt.setDouble(9, station.getZ());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertSignalBoundaries(Connection connection, List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries) throws SQLException {
        if (boundaries.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_signal_boundaries (boundary_id, graph_id, edge_id, position, group_id_primary, group_id_secondary, dimension, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.SignalBoundaryInfo boundary : boundaries) {
                stmt.setString(1, boundary.getBoundaryId());
                stmt.setString(2, boundary.getGraphId());
                stmt.setString(3, boundary.getEdgeId());
                stmt.setDouble(4, boundary.getPosition());
                stmt.setString(5, boundary.getGroupIdPrimary());
                stmt.setString(6, boundary.getGroupIdSecondary());
                stmt.setString(7, boundary.getDimension());
                stmt.setDouble(8, boundary.getX());
                stmt.setDouble(9, boundary.getY());
                stmt.setDouble(10, boundary.getZ());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void insertEdgeSegments(Connection connection, List<CreateNetworkSnapshot.EdgeSegmentInfo> segments) throws SQLException {
        if (segments.isEmpty()) {
            return;
        }
        try (PreparedStatement stmt = connection.prepareStatement(
            "INSERT INTO create_edge_segments (segment_id, edge_id, start_pos, end_pos, group_id) VALUES (?, ?, ?, ?, ?)")) {
            for (CreateNetworkSnapshot.EdgeSegmentInfo segment : segments) {
                stmt.setString(1, segment.getSegmentId());
                stmt.setString(2, segment.getEdgeId());
                stmt.setDouble(3, segment.getStartPos());
                stmt.setDouble(4, segment.getEndPos());
                stmt.setString(5, segment.getGroupId());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private List<CreateNetworkSnapshot.GraphInfo> queryGraphs(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ? "SELECT graph_id, checksum, color, updated_at FROM create_graphs" :
            "SELECT graph_id, checksum, color, updated_at FROM create_graphs WHERE graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.GraphInfo> graphs = new ArrayList<CreateNetworkSnapshot.GraphInfo>();
            while (rs.next()) {
                graphs.add(new CreateNetworkSnapshot.GraphInfo(
                    rs.getString("graph_id"),
                    rs.getInt("checksum"),
                    rs.getInt("color"),
                    rs.getLong("updated_at")
                ));
            }
            return graphs;
        }
    }

    private List<CreateNetworkSnapshot.NodeInfo> queryNodes(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ? "SELECT graph_id, node_net_id, dimension, x, y, z, normal_x, normal_y, normal_z, y_offset_pixels FROM create_nodes" :
            "SELECT graph_id, node_net_id, dimension, x, y, z, normal_x, normal_y, normal_z, y_offset_pixels FROM create_nodes WHERE graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.NodeInfo> nodes = new ArrayList<CreateNetworkSnapshot.NodeInfo>();
            while (rs.next()) {
                nodes.add(new CreateNetworkSnapshot.NodeInfo(
                    rs.getString("graph_id"),
                    rs.getInt("node_net_id"),
                    rs.getString("dimension"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getDouble("normal_x"),
                    rs.getDouble("normal_y"),
                    rs.getDouble("normal_z"),
                    rs.getInt("y_offset_pixels")
                ));
            }
            return nodes;
        }
    }

    private List<CreateNetworkSnapshot.EdgeInfo> queryEdges(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ? "SELECT edge_id, graph_id, node1_net_id, node2_net_id, is_turn, is_portal, length, material_id FROM create_edges" :
            "SELECT edge_id, graph_id, node1_net_id, node2_net_id, is_turn, is_portal, length, material_id FROM create_edges WHERE graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.EdgeInfo> edges = new ArrayList<CreateNetworkSnapshot.EdgeInfo>();
            while (rs.next()) {
                edges.add(new CreateNetworkSnapshot.EdgeInfo(
                    rs.getString("edge_id"),
                    rs.getString("graph_id"),
                    rs.getInt("node1_net_id"),
                    rs.getInt("node2_net_id"),
                    rs.getInt("is_turn") == 1,
                    rs.getInt("is_portal") == 1,
                    rs.getDouble("length"),
                    rs.getString("material_id")
                ));
            }
            return edges;
        }
    }

    private List<CreateNetworkSnapshot.EdgePolylinePoint> queryPolylines(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null
            ? "SELECT edge_id, seq, x, y, z FROM create_edge_polyline ORDER BY edge_id, seq"
            : "SELECT ep.edge_id, ep.seq, ep.x, ep.y, ep.z FROM create_edge_polyline ep INNER JOIN create_edges e ON ep.edge_id = e.edge_id WHERE e.graph_id = ? ORDER BY ep.edge_id, ep.seq";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.EdgePolylinePoint> points = new ArrayList<CreateNetworkSnapshot.EdgePolylinePoint>();
            while (rs.next()) {
                points.add(new CreateNetworkSnapshot.EdgePolylinePoint(
                    rs.getString("edge_id"),
                    rs.getInt("seq"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z")
                ));
            }
            return points;
        }
    }

    private List<CreateNetworkSnapshot.StationInfo> queryStations(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ? "SELECT station_id, graph_id, edge_id, position, name, dimension, x, y, z FROM create_stations" :
            "SELECT station_id, graph_id, edge_id, position, name, dimension, x, y, z FROM create_stations WHERE graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.StationInfo> stations = new ArrayList<CreateNetworkSnapshot.StationInfo>();
            while (rs.next()) {
                stations.add(new CreateNetworkSnapshot.StationInfo(
                    rs.getString("station_id"),
                    rs.getString("graph_id"),
                    rs.getString("edge_id"),
                    rs.getDouble("position"),
                    rs.getString("name"),
                    rs.getString("dimension"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z")
                ));
            }
            return stations;
        }
    }

    private List<CreateNetworkSnapshot.SignalBoundaryInfo> querySignalBoundaries(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ?
            "SELECT boundary_id, graph_id, edge_id, position, group_id_primary, group_id_secondary, dimension, x, y, z FROM create_signal_boundaries" :
            "SELECT boundary_id, graph_id, edge_id, position, group_id_primary, group_id_secondary, dimension, x, y, z FROM create_signal_boundaries WHERE graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.SignalBoundaryInfo> boundaries = new ArrayList<CreateNetworkSnapshot.SignalBoundaryInfo>();
            while (rs.next()) {
                boundaries.add(new CreateNetworkSnapshot.SignalBoundaryInfo(
                    rs.getString("boundary_id"),
                    rs.getString("graph_id"),
                    rs.getString("edge_id"),
                    rs.getDouble("position"),
                    rs.getString("group_id_primary"),
                    rs.getString("group_id_secondary"),
                    rs.getString("dimension"),
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z")
                ));
            }
            return boundaries;
        }
    }

    private List<CreateNetworkSnapshot.EdgeSegmentInfo> queryEdgeSegments(Connection connection, String graphId) throws SQLException {
        String sql = graphId == null ?
            "SELECT segment_id, edge_id, start_pos, end_pos, group_id FROM create_edge_segments" :
            "SELECT es.segment_id, es.edge_id, es.start_pos, es.end_pos, es.group_id FROM create_edge_segments es INNER JOIN create_edges e ON es.edge_id = e.edge_id WHERE e.graph_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            if (graphId != null) {
                stmt.setString(1, graphId);
            }
            ResultSet rs = stmt.executeQuery();
            List<CreateNetworkSnapshot.EdgeSegmentInfo> segments = new ArrayList<CreateNetworkSnapshot.EdgeSegmentInfo>();
            while (rs.next()) {
                segments.add(new CreateNetworkSnapshot.EdgeSegmentInfo(
                    rs.getString("segment_id"),
                    rs.getString("edge_id"),
                    rs.getDouble("start_pos"),
                    rs.getDouble("end_pos"),
                    rs.getString("group_id")
                ));
            }
            return segments;
        }
    }
}
