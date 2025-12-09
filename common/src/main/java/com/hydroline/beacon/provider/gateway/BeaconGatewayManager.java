package com.hydroline.beacon.provider.gateway;

import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.transport.BeaconRequestDispatcher;
import com.hydroline.beacon.provider.service.BeaconProviderService;
import java.nio.file.Path;
import java.util.Objects;

public final class BeaconGatewayManager {
    private final BeaconProviderService service;
    private final GatewayConfigLoader configLoader = new GatewayConfigLoader();
    private GatewayServer server;

    public BeaconGatewayManager(BeaconProviderService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public synchronized void start(Path configDir) {
        Objects.requireNonNull(configDir, "configDir");
        if (server != null) {
            return;
        }
        GatewayConfig config = configLoader.load(configDir);
        if (config.listenPort() <= 0) {
            BeaconProviderMod.LOGGER.info("Beacon Netty gateway disabled via config");
            return;
        }
        GatewayServer instance = new GatewayServer(config, new BeaconRequestDispatcher(service));
        instance.start();
        this.server = instance;
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.close();
            } finally {
                server = null;
            }
        }
    }
}
