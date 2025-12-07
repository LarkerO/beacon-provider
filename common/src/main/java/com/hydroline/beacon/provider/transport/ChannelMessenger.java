package com.hydroline.beacon.provider.transport;

import com.hydroline.beacon.provider.protocol.BeaconResponse;
import java.util.UUID;

/**
 * Abstraction for sending responses back through the plugin channel.
 */
public interface ChannelMessenger {
    void reply(UUID playerUuid, BeaconResponse response);
}
