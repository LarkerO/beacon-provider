package com.hydroline.beacon.provider.service;

import com.hydroline.beacon.provider.protocol.BeaconMessage;
import com.hydroline.beacon.provider.protocol.BeaconResponse;
import com.hydroline.beacon.provider.transport.PluginMessageContext;

/**
 * Strategy per action so loader code can register integrations (MTR, Create, etc.).
 */
public interface BeaconActionHandler {
    String action();

    BeaconResponse handle(BeaconMessage message, PluginMessageContext context);
}
