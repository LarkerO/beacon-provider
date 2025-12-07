package com.hydroline.beacon.provider.transport;

import java.time.Instant;
import java.util.UUID;

/**
 * Transport-layer metadata describing the origin of a plugin message.
 */
public final class PluginMessageContext {
    private final UUID playerUuid;
    private final String channel;
    private final Instant receivedAt;

    public PluginMessageContext(UUID playerUuid, String channel, Instant receivedAt) {
        this.playerUuid = playerUuid;
        this.channel = channel;
        this.receivedAt = receivedAt;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
