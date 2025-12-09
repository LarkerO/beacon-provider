package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrListDepotsActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:list_depots";

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
        String dimension = payload.has("dimension") ? payload.get("dimension").getAsString() : null;
        JsonObject responsePayload = new JsonObject();
        responsePayload.add("depots", MtrJsonWriter.writeDepots(gateway.fetchDepots(dimension)));
        return ok(message.getRequestId(), responsePayload);
    }
}
