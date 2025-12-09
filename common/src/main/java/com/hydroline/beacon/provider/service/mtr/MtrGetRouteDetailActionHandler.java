package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrGetRouteDetailActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_route_detail";

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
        if (!payload.has("dimension") || !payload.has("routeId")) {
            return invalidPayload(message.getRequestId(), "Expect dimension + routeId");
        }
        String dimension = payload.get("dimension").getAsString();
        long routeId = payload.get("routeId").getAsLong();
        return gateway.fetchRouteDetail(dimension, routeId)
            .map(detail -> ok(message.getRequestId(), MtrJsonWriter.writeRouteDetail(detail)))
            .orElseGet(() -> BeaconResponse.builder(message.getRequestId())
                .result(ResultCode.ERROR)
                .message("Route not found")
                .build());
    }
}
