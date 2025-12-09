package com.hydroline.beacon.provider.service;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.time.Duration;
import java.time.Instant;

/**
 * Simple built-in action that helps Bukkit check connectivity and latency.
 */
public final class PingActionHandler implements BeaconActionHandler {
    public static final String ACTION = "beacon:ping";

    @Override
    public String action() {
        return ACTION;
    }

    @Override
    public BeaconResponse handle(BeaconMessage message, TransportContext context) {
        JsonObject payload = new JsonObject();
        payload.addProperty("echo", message.getPayload().has("echo") ? message.getPayload().get("echo").getAsString() : "pong");
        payload.addProperty("receivedAt", context.getReceivedAt().toEpochMilli());
        payload.addProperty("latencyMs", Duration.between(context.getReceivedAt(), Instant.now()).abs().toMillis());
        return BeaconResponse.builder(message.getRequestId())
            .result(ResultCode.OK)
            .payload(payload)
            .build();
    }
}
