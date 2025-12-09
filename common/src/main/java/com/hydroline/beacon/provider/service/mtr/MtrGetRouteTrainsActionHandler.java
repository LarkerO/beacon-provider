package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrGetRouteTrainsActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_route_trains";

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
            return invalidPayload(message.getRequestId(), "dimension and routeId are required");
        }
        String dimension = payload.get("dimension").getAsString();
        long routeId;
        try {
            routeId = payload.get("routeId").getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return invalidPayload(message.getRequestId(), "routeId must be a number");
        }
        if (routeId <= 0) {
            return invalidPayload(message.getRequestId(), "routeId must be positive");
        }
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("dimension", dimension);
        responsePayload.addProperty("routeId", routeId);
        responsePayload.add("trains", MtrJsonWriter.writeTrainStatuses(gateway.fetchRouteTrains(dimension, routeId)));
        return ok(message.getRequestId(), responsePayload);
    }
}
