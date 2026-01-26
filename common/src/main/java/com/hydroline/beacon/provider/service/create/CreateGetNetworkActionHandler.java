package com.hydroline.beacon.provider.service.create;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.create.CreateJsonWriter;
import com.hydroline.beacon.provider.create.CreateNetworkSnapshot;
import com.hydroline.beacon.provider.create.CreateQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.Optional;

public final class CreateGetNetworkActionHandler extends AbstractCreateActionHandler {
    public static final String ACTION = "create:get_network";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        CreateQueryGateway gateway = gateway();
        if (!gateway.isReady()) {
            return notReady(message.getRequestId());
        }
        JsonObject payload = message.getPayload();
        String graphId = payload != null && payload.has("graphId") ? payload.get("graphId").getAsString() : null;
        boolean includePolylines = payload == null || !payload.has("includePolylines") || payload.get("includePolylines").getAsBoolean();
        Optional<CreateNetworkSnapshot> snapshot = gateway.fetchNetworkSnapshot(graphId, includePolylines);
        if (graphId != null && !snapshot.isPresent()) {
            return invalidPayload(message.getRequestId(), "unknown graphId");
        }
        CreateNetworkSnapshot networkSnapshot = snapshot.orElseGet(() -> new CreateNetworkSnapshot(null, null, null, null, null, null, null));
        JsonObject responsePayload = CreateJsonWriter.writeNetworkSnapshot(networkSnapshot, includePolylines);
        return ok(message.getRequestId(), responsePayload);
    }
}
