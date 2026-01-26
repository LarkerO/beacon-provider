package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.service.create.CreateGetNetworkActionHandler;
import com.hydroline.beacon.provider.service.create.CreateGetRealtimeActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRailwaySnapshotActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRouteTrainsActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetStationScheduleActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetAllStationSchedulesActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetDepotTrainsActionHandler;
import java.util.Arrays;

/**
 * Factory helpers to keep loader entrypoints concise.
 */
public final class BeaconServiceFactory {
    private BeaconServiceFactory() {
    }

    public static DefaultBeaconProviderService createDefault() {
        return new DefaultBeaconProviderService(Arrays.asList(
            new PingActionHandler(),
            new MtrGetRailwaySnapshotActionHandler(),
            new MtrGetRouteTrainsActionHandler(),
            new MtrGetStationScheduleActionHandler(),
            new MtrGetAllStationSchedulesActionHandler(),
            new MtrGetDepotTrainsActionHandler(),
            new CreateGetNetworkActionHandler(),
            new CreateGetRealtimeActionHandler()
        ));
    }
}
