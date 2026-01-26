package com.hydroline.beacon.provider.forge.create;

import com.hydroline.beacon.provider.create.CreateEdgeIdFactory;
import com.hydroline.beacon.provider.create.CreateRealtimeSnapshot;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageBogey;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class CreateRealtimeSnapshotBuilder {
    private CreateRealtimeSnapshotBuilder() {
    }

    static CreateRealtimeSnapshot capture(MinecraftServer server) {
        RailwaySavedData data = RailwaySavedData.load(server);
        if (data == null) {
            return null;
        }
        List<CreateRealtimeSnapshot.TrainStatus> trains = new ArrayList<CreateRealtimeSnapshot.TrainStatus>();
        for (Map.Entry<UUID, Train> entry : data.getTrains().entrySet()) {
            Train train = entry.getValue();
            trains.add(buildTrainStatus(train));
        }
        List<CreateRealtimeSnapshot.GroupStatus> groups = new ArrayList<CreateRealtimeSnapshot.GroupStatus>();
        for (Map.Entry<UUID, SignalEdgeGroup> entry : data.getSignalBlocks().entrySet()) {
            groups.add(buildGroupStatus(entry.getValue()));
        }
        return new CreateRealtimeSnapshot(System.currentTimeMillis(), trains, groups);
    }

    private static CreateRealtimeSnapshot.TrainStatus buildTrainStatus(Train train) {
        String trainId = train.id == null ? "" : train.id.toString();
        String name = train.name == null ? "" : train.name.getString();
        String iconId = train.icon == null ? null : train.icon.getId().toString();
        String status = train.status == null ? null : train.status.toString();
        String graphId = train.graph == null || train.graph.id == null ? null : train.graph.id.toString();
        String currentStationId = train.currentStation == null ? null : train.currentStation.toString();
        String scheduleTitle = train.runtime == null ? null : train.runtime.currentTitle;
        Integer scheduleEntry = train.runtime == null ? null : Integer.valueOf(train.runtime.currentEntry);
        String scheduleState = train.runtime == null || train.runtime.state == null ? null : train.runtime.state.name();
        boolean schedulePaused = train.runtime != null && train.runtime.paused;
        boolean scheduleCompleted = train.runtime != null && train.runtime.completed;
        boolean scheduleAuto = train.runtime != null && train.runtime.isAutoSchedule;

        List<CreateRealtimeSnapshot.DimensionPosition> positions = new ArrayList<CreateRealtimeSnapshot.DimensionPosition>();
        for (ResourceKey<Level> dimension : train.getPresentDimensions()) {
            BlockPos pos = train.getPositionInDimension(dimension).orElse(null);
            if (pos != null) {
                positions.add(new CreateRealtimeSnapshot.DimensionPosition(
                    dimension.location().toString(),
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                ));
            }
        }

        List<CreateRealtimeSnapshot.CarriageInfo> carriages = new ArrayList<CreateRealtimeSnapshot.CarriageInfo>();
        if (train.carriages != null) {
            for (Carriage carriage : train.carriages) {
                carriages.add(buildCarriageInfo(train.graph, carriage));
            }
        }

        return new CreateRealtimeSnapshot.TrainStatus(
            trainId,
            name,
            iconId,
            train.mapColorIndex,
            status,
            train.speed,
            train.targetSpeed,
            train.throttle,
            train.derailed,
            graphId,
            currentStationId,
            scheduleTitle,
            scheduleEntry,
            scheduleState,
            schedulePaused,
            scheduleCompleted,
            scheduleAuto,
            positions,
            carriages
        );
    }

    private static CreateRealtimeSnapshot.CarriageInfo buildCarriageInfo(TrackGraph graph, Carriage carriage) {
        CreateRealtimeSnapshot.TravellingPointInfo leading = buildTravellingPointInfo(graph, carriage.getLeadingPoint());
        CreateRealtimeSnapshot.TravellingPointInfo trailing = buildTravellingPointInfo(graph, carriage.getTrailingPoint());
        CreateRealtimeSnapshot.BogeyInfo leadingBogey = buildBogeyInfo(carriage.leadingBogey());
        CreateRealtimeSnapshot.BogeyInfo trailingBogey = buildBogeyInfo(carriage.trailingBogey());
        return new CreateRealtimeSnapshot.CarriageInfo(
            carriage.id,
            carriage.bogeySpacing,
            leading,
            trailing,
            leadingBogey,
            trailingBogey
        );
    }

    private static CreateRealtimeSnapshot.BogeyInfo buildBogeyInfo(CarriageBogey bogey) {
        if (bogey == null) {
            return null;
        }
        String styleId = bogey.getStyle() == null ? null : bogey.getStyle().id.toString();
        String size = bogey.getSize() == null ? null : bogey.getSize().id().toString();
        return new CreateRealtimeSnapshot.BogeyInfo(styleId, size, bogey.isUpsideDown());
    }

    private static CreateRealtimeSnapshot.TravellingPointInfo buildTravellingPointInfo(TrackGraph graph, TravellingPoint point) {
        if (point == null || graph == null) {
            return null;
        }
        TrackEdge edge = point.edge;
        TrackNode node1 = point.node1;
        TrackNode node2 = point.node2;
        int node1NetId = node1 == null ? -1 : node1.getNetId();
        int node2NetId = node2 == null ? -1 : node2.getNetId();
        String dimension = null;
        if (node1 != null && node1.getLocation() != null && node1.getLocation().getDimension() != null) {
            dimension = node1.getLocation().getDimension().location().toString();
        }
        Vec3 position = point.getPosition(graph);
        String edgeId = null;
        if (graph != null && edge != null && edge.node1 != null && edge.node2 != null) {
            String materialId = edge.getTrackMaterial() == null ? null : edge.getTrackMaterial().id.toString();
            edgeId = CreateEdgeIdFactory.build(
                graph.id.toString(),
                edge.node1.getNetId(),
                edge.node2.getNetId(),
                edge.isTurn(),
                edge.isInterDimensional(),
                edge.getLength(),
                materialId
            );
        }
        return new CreateRealtimeSnapshot.TravellingPointInfo(
            edgeId,
            node1NetId,
            node2NetId,
            point.position,
            dimension,
            position.x,
            position.y,
            position.z
        );
    }

    private static CreateRealtimeSnapshot.GroupStatus buildGroupStatus(SignalEdgeGroup group) {
        String groupId = group.id == null ? "" : group.id.toString();
        String color = group.color == null ? null : group.color.name();
        String reservedBoundaryId = group.reserved == null || group.reserved.getId() == null ? null : group.reserved.getId().toString();
        List<String> trainIds = new ArrayList<String>();
        if (group.trains != null) {
            for (Train train : group.trains) {
                if (train != null && train.id != null) {
                    trainIds.add(train.id.toString());
                }
            }
        }
        return new CreateRealtimeSnapshot.GroupStatus(groupId, color, reservedBoundaryId, trainIds);
    }
}
