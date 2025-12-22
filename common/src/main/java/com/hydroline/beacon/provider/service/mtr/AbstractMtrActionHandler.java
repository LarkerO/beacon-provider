package com.hydroline.beacon.provider.service.mtr;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.mtr.MtrQueryGateway;
import com.hydroline.beacon.provider.mtr.MtrQueryRegistry;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ResultCode;

abstract class AbstractMtrActionHandler implements com.hydroline.beacon.provider.service.BeaconActionHandler {
    protected MtrQueryGateway gateway() {
        return MtrQueryRegistry.get();
    }

    protected BeaconResponse notReady(String requestId) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.NOT_READY)
            .message("MTR data not available")
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

    protected BeaconResponse busy(String requestId, String reason) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.BUSY)
            .message(reason)
            .build();
    }

    protected BeaconResponse error(String requestId, String reason) {
        return BeaconResponse.builder(requestId)
            .result(ResultCode.ERROR)
            .message(reason)
            .build();
    }

    @Override
    public abstract BeaconResponse handle(BeaconMessage message, com.hydroline.beacon.provider.transport.TransportContext context);
}
