package com.hydroline.beacon.provider.service.create;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.create.CreateQueryGateway;
import com.hydroline.beacon.provider.create.CreateQueryRegistry;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ResultCode;

abstract class AbstractCreateActionHandler implements com.hydroline.beacon.provider.service.BeaconActionHandler {
    protected CreateQueryGateway gateway() {
        return CreateQueryRegistry.get();
    }

    protected BeaconResponse notReady(String requestId) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.NOT_READY)
            .message("Create data not available")
            .build();
    }

    protected BeaconResponse invalidPayload(String requestId, String reason) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.INVALID_PAYLOAD)
            .message(reason)
            .build();
    }

    protected BeaconResponse ok(String requestId, JsonObject payload) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.OK)
            .payload(payload)
            .build();
    }

    protected BeaconResponse error(String requestId, String reason) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.ERROR)
            .message(reason)
            .build();
    }

    @Override
    public abstract BeaconResponse handle(com.hydroline.beacon.provider.protocol.BeaconMessage message,
                                          com.hydroline.beacon.provider.transport.TransportContext context);
}
