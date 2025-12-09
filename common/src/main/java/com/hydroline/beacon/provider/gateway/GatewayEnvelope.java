package com.hydroline.beacon.provider.gateway;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class GatewayEnvelope {
    private final GatewayMessageType type;
    private final UUID connectionId;
    private final JsonObject body;

    public GatewayEnvelope(GatewayMessageType type, UUID connectionId, JsonObject body) {
        this.type = type;
        this.connectionId = connectionId;
        this.body = body;
    }

    public GatewayMessageType type() {
        return type;
    }

    public UUID connectionId() {
        return connectionId;
    }

    public JsonObject body() {
        return body;
    }
}
