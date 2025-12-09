package com.hydroline.beacon.provider.transport;

import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.service.BeaconProviderService;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Glue code used by loader-specific entrypoints to wire channel events into the shared service.
 */
public final class ChannelMessageRouter {
    private final BeaconRequestDispatcher dispatcher;
    private final ChannelMessenger messenger;

    public ChannelMessageRouter(BeaconProviderService service, ChannelMessenger messenger) {
        this.dispatcher = new BeaconRequestDispatcher(Objects.requireNonNull(service, "service"));
        this.messenger = Objects.requireNonNull(messenger, "messenger");
    }

    public void handleIncoming(UUID playerUuid, byte[] payload) {
        TransportContext context = new TransportContext(playerUuid, TransportKind.PLUGIN_MESSAGE, Instant.now());
        BeaconResponse response = dispatcher.dispatch(payload, context);
        messenger.reply(playerUuid, response);
    }
}
