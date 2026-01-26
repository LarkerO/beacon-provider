package com.hydroline.beacon.provider.create;

import java.util.Collections;
import java.util.List;

public final class CreateRealtimeSnapshot {
    private static final CreateRealtimeSnapshot EMPTY = new CreateRealtimeSnapshot(0L, Collections.<TrainStatus>emptyList(), Collections.<GroupStatus>emptyList());

    private final long capturedAt;
    private final List<TrainStatus> trains;
    private final List<GroupStatus> groups;

    public CreateRealtimeSnapshot(long capturedAt, List<TrainStatus> trains, List<GroupStatus> groups) {
        this.capturedAt = capturedAt;
        this.trains = trains == null ? Collections.<TrainStatus>emptyList() : trains;
        this.groups = groups == null ? Collections.<GroupStatus>emptyList() : groups;
    }

    public static CreateRealtimeSnapshot empty() {
        return EMPTY;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public List<TrainStatus> getTrains() {
        return trains;
    }

    public List<GroupStatus> getGroups() {
        return groups;
    }

    public static final class TrainStatus {
        private final String trainId;
        private final String name;
        private final String iconId;
        private final int mapColorIndex;
        private final String status;
        private final double speed;
        private final double targetSpeed;
        private final double throttle;
        private final boolean derailed;
        private final String graphId;
        private final String currentStationId;
        private final String scheduleTitle;
        private final Integer scheduleEntry;
        private final String scheduleState;
        private final boolean schedulePaused;
        private final boolean scheduleCompleted;
        private final boolean scheduleAuto;
        private final List<DimensionPosition> positions;
        private final List<CarriageInfo> carriages;

        public TrainStatus(String trainId,
                           String name,
                           String iconId,
                           int mapColorIndex,
                           String status,
                           double speed,
                           double targetSpeed,
                           double throttle,
                           boolean derailed,
                           String graphId,
                           String currentStationId,
                           String scheduleTitle,
                           Integer scheduleEntry,
                           String scheduleState,
                           boolean schedulePaused,
                           boolean scheduleCompleted,
                           boolean scheduleAuto,
                           List<DimensionPosition> positions,
                           List<CarriageInfo> carriages) {
            this.trainId = trainId;
            this.name = name;
            this.iconId = iconId;
            this.mapColorIndex = mapColorIndex;
            this.status = status;
            this.speed = speed;
            this.targetSpeed = targetSpeed;
            this.throttle = throttle;
            this.derailed = derailed;
            this.graphId = graphId;
            this.currentStationId = currentStationId;
            this.scheduleTitle = scheduleTitle;
            this.scheduleEntry = scheduleEntry;
            this.scheduleState = scheduleState;
            this.schedulePaused = schedulePaused;
            this.scheduleCompleted = scheduleCompleted;
            this.scheduleAuto = scheduleAuto;
            this.positions = positions == null ? Collections.<DimensionPosition>emptyList() : positions;
            this.carriages = carriages == null ? Collections.<CarriageInfo>emptyList() : carriages;
        }

        public String getTrainId() {
            return trainId;
        }

        public String getName() {
            return name;
        }

        public String getIconId() {
            return iconId;
        }

        public int getMapColorIndex() {
            return mapColorIndex;
        }

        public String getStatus() {
            return status;
        }

        public double getSpeed() {
            return speed;
        }

        public double getTargetSpeed() {
            return targetSpeed;
        }

        public double getThrottle() {
            return throttle;
        }

        public boolean isDerailed() {
            return derailed;
        }

        public String getGraphId() {
            return graphId;
        }

        public String getCurrentStationId() {
            return currentStationId;
        }

        public String getScheduleTitle() {
            return scheduleTitle;
        }

        public Integer getScheduleEntry() {
            return scheduleEntry;
        }

        public String getScheduleState() {
            return scheduleState;
        }

        public boolean isSchedulePaused() {
            return schedulePaused;
        }

        public boolean isScheduleCompleted() {
            return scheduleCompleted;
        }

        public boolean isScheduleAuto() {
            return scheduleAuto;
        }

        public List<DimensionPosition> getPositions() {
            return positions;
        }

        public List<CarriageInfo> getCarriages() {
            return carriages;
        }
    }

    public static final class DimensionPosition {
        private final String dimension;
        private final double x;
        private final double y;
        private final double z;

        public DimensionPosition(String dimension, double x, double y, double z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
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

    public static final class CarriageInfo {
        private final int id;
        private final int bogeySpacing;
        private final TravellingPointInfo leading;
        private final TravellingPointInfo trailing;
        private final BogeyInfo leadingBogey;
        private final BogeyInfo trailingBogey;

        public CarriageInfo(int id, int bogeySpacing,
                            TravellingPointInfo leading, TravellingPointInfo trailing,
                            BogeyInfo leadingBogey, BogeyInfo trailingBogey) {
            this.id = id;
            this.bogeySpacing = bogeySpacing;
            this.leading = leading;
            this.trailing = trailing;
            this.leadingBogey = leadingBogey;
            this.trailingBogey = trailingBogey;
        }

        public int getId() {
            return id;
        }

        public int getBogeySpacing() {
            return bogeySpacing;
        }

        public TravellingPointInfo getLeading() {
            return leading;
        }

        public TravellingPointInfo getTrailing() {
            return trailing;
        }

        public BogeyInfo getLeadingBogey() {
            return leadingBogey;
        }

        public BogeyInfo getTrailingBogey() {
            return trailingBogey;
        }
    }

    public static final class TravellingPointInfo {
        private final String edgeId;
        private final int node1NetId;
        private final int node2NetId;
        private final double position;
        private final String dimension;
        private final double x;
        private final double y;
        private final double z;

        public TravellingPointInfo(String edgeId, int node1NetId, int node2NetId,
                                   double position, String dimension,
                                   double x, double y, double z) {
            this.edgeId = edgeId;
            this.node1NetId = node1NetId;
            this.node2NetId = node2NetId;
            this.position = position;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public int getNode1NetId() {
            return node1NetId;
        }

        public int getNode2NetId() {
            return node2NetId;
        }

        public double getPosition() {
            return position;
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

    public static final class BogeyInfo {
        private final String styleId;
        private final String size;
        private final boolean upsideDown;

        public BogeyInfo(String styleId, String size, boolean upsideDown) {
            this.styleId = styleId;
            this.size = size;
            this.upsideDown = upsideDown;
        }

        public String getStyleId() {
            return styleId;
        }

        public String getSize() {
            return size;
        }

        public boolean isUpsideDown() {
            return upsideDown;
        }
    }

    public static final class GroupStatus {
        private final String groupId;
        private final String color;
        private final String reservedBoundaryId;
        private final List<String> trainIds;

        public GroupStatus(String groupId, String color, String reservedBoundaryId, List<String> trainIds) {
            this.groupId = groupId;
            this.color = color;
            this.reservedBoundaryId = reservedBoundaryId;
            this.trainIds = trainIds == null ? Collections.<String>emptyList() : trainIds;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getColor() {
            return color;
        }

        public String getReservedBoundaryId() {
            return reservedBoundaryId;
        }

        public List<String> getTrainIds() {
            return trainIds;
        }
    }
}
