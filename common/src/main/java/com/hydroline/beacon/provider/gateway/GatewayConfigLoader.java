package com.hydroline.beacon.provider.gateway;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.hydroline.beacon.provider.BeaconProviderMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public final class GatewayConfigLoader {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path RELATIVE_PATH = Paths.get("hydroline", "beacon-provider.json");

    public GatewayConfig load(Path configRoot) {
        Objects.requireNonNull(configRoot, "configRoot");
        try {
            Path file = configRoot.resolve(RELATIVE_PATH);
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                GatewayConfig defaults = GatewayConfig.defaults();
                write(file, defaults);
                BeaconProviderMod.LOGGER.info("Created default Beacon gateway config at {}", file);
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                return fromJson(json);
            }
        } catch (IOException | JsonParseException ex) {
            BeaconProviderMod.LOGGER.error("Failed to load gateway config, falling back to defaults", ex);
            return GatewayConfig.defaults();
        }
    }

    private void write(Path file, GatewayConfig config) throws IOException {
        JsonObject json = toJson(config);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        }
    }

    private GatewayConfig fromJson(JsonObject json) {
        if (json == null) {
            return GatewayConfig.defaults();
        }
        GatewayConfig.Builder builder = GatewayConfig.builder();
        builder.listenAddress(json.has("listenAddress") ? json.get("listenAddress").getAsString() : "127.0.0.1");
        builder.listenPort(json.has("listenPort") ? json.get("listenPort").getAsInt() : 28545);
        builder.authToken(json.has("authToken") ? json.get("authToken").getAsString() : "change-me");
        builder.handshakeTimeoutSeconds(json.has("handshakeTimeoutSeconds") ? json.get("handshakeTimeoutSeconds").getAsInt() : 10);
        builder.idleTimeoutSeconds(json.has("idleTimeoutSeconds") ? json.get("idleTimeoutSeconds").getAsInt() : 240);
        try {
            return builder.build();
        } catch (IllegalStateException ex) {
            BeaconProviderMod.LOGGER.error("Invalid gateway config, using defaults", ex);
            return GatewayConfig.defaults();
        }
    }

    private JsonObject toJson(GatewayConfig config) {
        JsonObject json = new JsonObject();
        json.addProperty("listenAddress", config.listenAddress());
        json.addProperty("listenPort", config.listenPort());
        json.addProperty("authToken", config.authToken());
        json.addProperty("handshakeTimeoutSeconds", config.handshakeTimeoutSeconds());
        json.addProperty("idleTimeoutSeconds", config.idleTimeoutSeconds());
        return json;
    }
}
