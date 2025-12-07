package com.hydroline.beacon.provider.service;

import java.util.Arrays;

/**
 * Factory helpers to keep loader entrypoints concise.
 */
public final class BeaconServiceFactory {
    private BeaconServiceFactory() {
    }

    public static DefaultBeaconProviderService createDefault() {
        return new DefaultBeaconProviderService(Arrays.asList(
            new PingActionHandler()
        ));
    }
}
