package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.PluginMessageContext;

public final class MtrGetDepotTrainsActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:get_depot_trains";

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
        if (!payload.has("dimension") || !payload.has("depotId")) {
            return invalidPayload(message.getRequestId(), "dimension and depotId are required");
        }
        String dimension = payload.get("dimension").getAsString();
        long depotId;
        try {
            depotId = payload.get("depotId").getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException ex) {
            return invalidPayload(message.getRequestId(), "depotId must be a number");
        }
        if (depotId <= 0) {
            return invalidPayload(message.getRequestId(), "depotId must be positive");
        }
        JsonObject responsePayload = new JsonObject();
        responsePayload.addProperty("dimension", dimension);
        responsePayload.addProperty("depotId", depotId);
        responsePayload.add("trains", MtrJsonWriter.writeTrainStatuses(gateway.fetchDepotTrains(dimension, depotId)));
        return ok(message.getRequestId(), responsePayload);
    }
}
