package com.hydroline.beacon.provider.transport;

import com.google.gson.JsonParseException;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.MessageSerializer;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.service.BeaconProviderService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses Beacon messages and delegates to {@link BeaconProviderService}.
 */
public final class BeaconRequestDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(BeaconRequestDispatcher.class);

    private final BeaconProviderService service;

    public BeaconRequestDispatcher(BeaconProviderService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public BeaconResponse dispatch(byte[] payload, TransportContext context) {
        try {
            BeaconMessage message = MessageSerializer.deserialize(payload);
            return service.handle(message, context);
        } catch (JsonParseException ex) {
            LOGGER.warn("Invalid JSON from {} via {}: {}", context.getOriginId(), context.getKind(), ex.getMessage());
            return BeaconResponse.builder("invalid")
                .result(ResultCode.INVALID_PAYLOAD)
                .message("JSON parse error: " + ex.getMessage())
                .build();
        } catch (Exception ex) {
            LOGGER.error("Failed to handle message from {} via {}", context.getOriginId(), context.getKind(), ex);
            return BeaconResponse.builder("internal")
                .result(ResultCode.ERROR)
                .message("Internal error")
                .build();
        }
    }
}
