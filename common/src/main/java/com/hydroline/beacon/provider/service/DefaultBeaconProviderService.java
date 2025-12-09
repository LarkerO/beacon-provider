package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.protocol.ChannelConstants;
import com.hydroline.beacon.provider.protocol.ResultCode;
import com.hydroline.beacon.provider.transport.TransportContext;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of beacon actions with sensible fallbacks.
 */
public final class DefaultBeaconProviderService implements BeaconProviderService {
    private final Map<String, BeaconActionHandler> handlers = new ConcurrentHashMap<>();

    public DefaultBeaconProviderService(Collection<BeaconActionHandler> initialHandlers) {
        if (initialHandlers != null) {
            initialHandlers.forEach(this::register);
        }
    }

    public DefaultBeaconProviderService register(BeaconActionHandler handler) {
        Objects.requireNonNull(handler, "handler");
        String action = handler.action();
        if (action == null || action.isEmpty()) {
            throw new IllegalArgumentException("action cannot be empty");
        }
        handlers.put(action, handler);
        return this;
    }

    @Override
    public BeaconResponse handle(BeaconMessage request, TransportContext context) {
        if (request.getProtocolVersion() != ChannelConstants.PROTOCOL_VERSION) {
            return BeaconResponse.builder(request.getRequestId())
                .result(ResultCode.INVALID_PAYLOAD)
                .message("Unsupported protocol version: " + request.getProtocolVersion())
                .build();
        }

        BeaconActionHandler handler = handlers.getOrDefault(request.getAction(), handlers.get(ChannelConstants.DEFAULT_ACTION));
        if (handler == null) {
            return BeaconResponse.builder(request.getRequestId())
                .result(ResultCode.INVALID_ACTION)
                .message("Unknown action: " + request.getAction())
                .build();
        }

        try {
            return handler.handle(request, context);
        } catch (Exception ex) {
            return BeaconResponse.builder(request.getRequestId())
                .result(ResultCode.ERROR)
                .message("Handler error: " + ex.getMessage())
                .build();
        }
    }
}
