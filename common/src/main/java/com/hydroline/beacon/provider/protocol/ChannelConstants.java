package com.hydroline.beacon.provider.protocol;

/**
 * Shared constants for the lightweight JSON protocol used by Bukkit plugins and the mod.
 */
public final class ChannelConstants {
    public static final String CHANNEL_NAMESPACE = "hydroline";
    public static final String CHANNEL_NAME = CHANNEL_NAMESPACE + ":beacon_provider";
    public static final int PROTOCOL_VERSION = 1;
    public static final String DEFAULT_ACTION = "beacon:invoke";
    public static final int MAX_PAYLOAD_BYTES = 2 * 1024; // Plugin channel limit is 32KB, keep JSON small.

    private ChannelConstants() {
    }
}
