package com.hydroline.beacon.provider.mtr;

import com.hydroline.beacon.provider.mtr.MtrModels.DimensionOverview;
import com.hydroline.beacon.provider.mtr.MtrModels.FareAreaInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.NodePage;
import com.hydroline.beacon.provider.mtr.MtrModels.RouteDetail;
import com.hydroline.beacon.provider.mtr.MtrModels.StationTimetable;
import com.hydroline.beacon.provider.mtr.MtrModels.StationInfo;
import com.hydroline.beacon.provider.mtr.MtrModels.TrainStatus;
import com.hydroline.beacon.provider.mtr.MtrModels.DepotInfo;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Loader-side service that knows how to fetch MTR data for the current Minecraft server.
 */
public interface MtrQueryGateway {
    /**
     * @return {@code true} if the loader has registered a working implementation and MTR is loaded.
     */
    default boolean isReady() {
        return true;
    }

    List<DimensionOverview> fetchNetworkOverview();

    Optional<RouteDetail> fetchRouteDetail(String dimensionId, long routeId);

    List<DepotInfo> fetchDepots(String dimensionId);

    List<FareAreaInfo> fetchFareAreas(String dimensionId);

    NodePage fetchNodes(String dimensionId, String cursor, int limit);

    Optional<StationTimetable> fetchStationTimetable(String dimensionId, long stationId, Long platformId);

    List<StationInfo> fetchStations(String dimensionId);

    List<TrainStatus> fetchRouteTrains(String dimensionId, long routeId);

    List<TrainStatus> fetchDepotTrains(String dimensionId, long depotId);

    MtrQueryGateway UNAVAILABLE = new MtrQueryGateway() {
        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public List<DimensionOverview> fetchNetworkOverview() {
            return Collections.emptyList();
        }

        @Override
        public Optional<RouteDetail> fetchRouteDetail(String dimensionId, long routeId) {
            return Optional.empty();
        }

        @Override
        public List<DepotInfo> fetchDepots(String dimensionId) {
            return Collections.emptyList();
        }

        @Override
        public List<FareAreaInfo> fetchFareAreas(String dimensionId) {
            return Collections.emptyList();
        }

        @Override
        public NodePage fetchNodes(String dimensionId, String cursor, int limit) {
            return new NodePage(dimensionId == null ? "" : dimensionId, Collections.<MtrModels.NodeInfo>emptyList(), null);
        }

        @Override
        public Optional<StationTimetable> fetchStationTimetable(String dimensionId, long stationId, Long platformId) {
            return Optional.empty();
        }

        @Override
        public List<StationInfo> fetchStations(String dimensionId) {
            return Collections.emptyList();
        }

        @Override
        public List<TrainStatus> fetchRouteTrains(String dimensionId, long routeId) {
            return Collections.emptyList();
        }

        @Override
        public List<TrainStatus> fetchDepotTrains(String dimensionId, long depotId) {
            return Collections.emptyList();
        }
    };
}
