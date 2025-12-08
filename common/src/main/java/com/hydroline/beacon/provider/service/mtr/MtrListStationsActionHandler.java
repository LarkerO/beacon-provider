package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.PluginMessageContext;

public final class MtrListStationsActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:list_stations";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, PluginMessageContext context) {
        MtrQueryGateway gateway = gateway();
        if (!gateway.isReady()) {
            return notReady(message.getRequestId());
        }
        JsonObject payload = message.getPayload();
        String dimension = payload.has("dimension") ? payload.get("dimension").getAsString() : null;
        JsonObject responsePayload = new JsonObject();
        if (dimension != null && !dimension.isEmpty()) {
            responsePayload.addProperty("dimension", dimension);
        }
        responsePayload.add("stations", MtrJsonWriter.writeStations(gateway.fetchStations(dimension)));
        return ok(message.getRequestId(), responsePayload);
    }
}
