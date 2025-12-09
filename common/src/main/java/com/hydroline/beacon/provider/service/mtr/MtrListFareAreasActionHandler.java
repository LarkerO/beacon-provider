package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrListFareAreasActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:list_fare_areas";

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
        if (!payload.has("dimension")) {
            return invalidPayload(message.getRequestId(), "Expect dimension");
        }
        String dimension = payload.get("dimension").getAsString();
        JsonObject responsePayload = new JsonObject();
        responsePayload.add("fareAreas", MtrJsonWriter.writeFareAreas(gateway.fetchFareAreas(dimension)));
        responsePayload.addProperty("dimension", dimension);
        return ok(message.getRequestId(), responsePayload);
    }
}
