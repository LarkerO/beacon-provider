package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrGetStationTimetableActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_station_timetable";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        MtrQueryGateway gateway = gateway();
        if (!gateway.isReady()) {
            return notReady(message.getRequestId());
        }
        JsonObject payload = message.getPayload();
        if (!payload.has("dimension") || !payload.has("stationId")) {
            return invalidPayload(message.getRequestId(), "Expect dimension + stationId");
        }
        String dimension = payload.get("dimension").getAsString();
        long stationId = payload.get("stationId").getAsLong();
        Long platformId = payload.has("platformId") ? payload.get("platformId").getAsLong() : null;
        return gateway.fetchStationTimetable(dimension, stationId, platformId)
            .map(timetable -> ok(message.getRequestId(), MtrJsonWriter.writeStationTimetable(timetable)))
            .orElseGet(() -> BeaconResponse.builder(message.getRequestId())
                .result(ResultCode.ERROR)
                .message("Timetable not found")
                .build());
    }
}
