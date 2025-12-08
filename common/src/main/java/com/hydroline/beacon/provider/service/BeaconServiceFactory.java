package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.service.mtr.MtrGetDepotTrainsActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRouteDetailActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetRouteTrainsActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrGetStationTimetableActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrListDepotsActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrListFareAreasActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrListNetworkOverviewActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrListNodesPaginatedActionHandler;
import com.hydroline.beacon.provider.service.mtr.MtrListStationsActionHandler;
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
            new MtrListNetworkOverviewActionHandler(),
            new MtrGetRouteDetailActionHandler(),
            new MtrListDepotsActionHandler(),
            new MtrListFareAreasActionHandler(),
            new MtrListNodesPaginatedActionHandler(),
            new MtrGetStationTimetableActionHandler(),
            new MtrListStationsActionHandler(),
            new MtrGetRouteTrainsActionHandler(),
            new MtrGetDepotTrainsActionHandler()
        ));
    }
}
