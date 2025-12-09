package com.hydroline.beacon.provider.transport;

/**
 * Indicates which low-level transport carried a Beacon request.
 */
public enum TransportKind {
    PLUGIN_MESSAGE,
    NETTY_GATEWAY
}
