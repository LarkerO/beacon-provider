package com.hydroline.beacon.provider.transport;

import com.google.gson.JsonParseException;
import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ChannelConstants;
import com.hydroline.beacon.provider.protocol.MessageSerializer;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.service.BeaconProviderService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Glue code used by loader-specific entrypoints to wire channel events into the shared service.
 */
public final class ChannelMessageRouter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChannelMessageRouter.class);

    private final BeaconProviderService service;
    private final ChannelMessenger messenger;

    public ChannelMessageRouter(BeaconProviderService service, ChannelMessenger messenger) {
        this.service = Objects.requireNonNull(service, "service");
        this.messenger = Objects.requireNonNull(messenger, "messenger");
    }

    public void handleIncoming(UUID playerUuid, byte[] payload) {
        try {
            BeaconMessage message = MessageSerializer.deserialize(payload);
            PluginMessageContext context = new PluginMessageContext(playerUuid, ChannelConstants.CHANNEL_NAME, Instant.now());
            BeaconResponse response = service.handle(message, context);
            messenger.reply(playerUuid, response);
        } catch (JsonParseException ex) {
            LOGGER.warn("Invalid JSON from {}: {}", playerUuid, ex.getMessage());
            BeaconResponse response = BeaconResponse.builder("invalid")
                .result(ResultCode.INVALID_PAYLOAD)
                .message("JSON parse error: " + ex.getMessage())
                .build();
            messenger.reply(playerUuid, response);
        } catch (Exception ex) {
            LOGGER.error("Failed to handle message from {}", playerUuid, ex);
            BeaconResponse response = BeaconResponse.builder("internal")
                .result(ResultCode.ERROR)
                .message("Internal error")
                .build();
            messenger.reply(playerUuid, response);
        }
    }
}
