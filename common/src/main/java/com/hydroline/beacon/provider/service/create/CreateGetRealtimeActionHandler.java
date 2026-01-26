package com.hydroline.beacon.provider.service.create;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.create.CreateJsonWriter;
import com.hydroline.beacon.provider.create.CreateQueryGateway;
import com.hydroline.beacon.provider.create.CreateRealtimeSnapshot;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class CreateGetRealtimeActionHandler extends AbstractCreateActionHandler {
    public static final String ACTION = "create:get_realtime";

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
        CreateRealtimeSnapshot snapshot = gateway.fetchRealtimeSnapshot();
        JsonObject responsePayload = CreateJsonWriter.writeRealtimeSnapshot(snapshot);
        return ok(message.getRequestId(), responsePayload);
    }
}
