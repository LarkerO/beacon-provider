package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.PluginMessageContext;

/**
 * Minimal API exposed to loader-specific entrypoints, decoupling Bukkit traffic from mod internals.
 */
public interface BeaconProviderService {
    BeaconResponse handle(BeaconMessage request, PluginMessageContext context);
}
