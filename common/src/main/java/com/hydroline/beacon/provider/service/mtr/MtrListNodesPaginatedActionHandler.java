package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrJsonWriter;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.TransportContext;

public final class MtrListNodesPaginatedActionHandler extends AbstractMtrActionHandler {
    public static final String ACTION = "mtr:list_nodes_paginated";
    private static final int MAX_LIMIT = 2048;
    private static final int DEFAULT_LIMIT = 512;

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
        String cursor = payload.has("cursor") ? payload.get("cursor").getAsString() : null;
        int limit = DEFAULT_LIMIT;
        if (payload.has("limit")) {
            limit = Math.max(1, Math.min(MAX_LIMIT, payload.get("limit").getAsInt()));
        }
        JsonObject responsePayload = MtrJsonWriter.writeNodePage(gateway.fetchNodes(dimension, cursor, limit));
        return ok(message.getRequestId(), responsePayload);
    }
}
