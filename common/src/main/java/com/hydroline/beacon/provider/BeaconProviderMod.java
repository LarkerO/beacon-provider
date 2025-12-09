package com.hydroline.beacon.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared bootstrap used by every loader-specific entrypoint.
 */
public final class BeaconProviderMod {
    public static final String MOD_ID = "beaconprovider";
    public static final String MOD_NAME = "Hydroline Beacon Provider";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    private static final String VERSION = resolveVersion();

    private BeaconProviderMod() {
    }

    public static void init() {
        LOGGER.info("Loaded {}", MOD_NAME);
    }

    public static String getVersion() {
        return VERSION;
    }

    private static String resolveVersion() {
        Package pkg = BeaconProviderMod.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        return "dev";
    }
}
