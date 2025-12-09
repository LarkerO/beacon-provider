package com.hydroline.beacon.provider.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Helper that converts between byte arrays and the JSON envelopes shared with Bukkit.
 */
public final class MessageSerializer {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MessageSerializer() {
    }

    public static byte[] serialize(BeaconResponse response) {
        return GSON.toJson(serializeToJson(response)).getBytes(StandardCharsets.UTF_8);
    }

    public static BeaconMessage deserialize(byte[] data) throws JsonParseException {
        Objects.requireNonNull(data, "data");
        JsonObject json = GSON.fromJson(new String(data, StandardCharsets.UTF_8), JsonObject.class);
        return deserialize(json);
    }

    public static BeaconMessage deserialize(JsonObject json) throws JsonParseException {
        Objects.requireNonNull(json, "json");
        int protocolVersion = json.get("protocolVersion").getAsInt();
        if (!json.has("requestId")) {
            throw new JsonParseException("Missing requestId");
        }
        String requestId = json.get("requestId").getAsString();
        if (!RequestId.isValid(requestId)) {
            throw new JsonParseException("requestId must be " + RequestId.LENGTH + " [0-9a-z] chars");
        }
        String action = json.has("action") ? json.get("action").getAsString() : ChannelConstants.DEFAULT_ACTION;
        JsonObject payload = json.has("payload") && json.get("payload").isJsonObject()
            ? json.getAsJsonObject("payload") : new JsonObject();
        return new BeaconMessage(protocolVersion, requestId, action, payload);
    }

    public static JsonObject serializeToJson(BeaconResponse response) {
        Objects.requireNonNull(response, "response");
        JsonObject json = new JsonObject();
        json.addProperty("protocolVersion", ChannelConstants.PROTOCOL_VERSION);
        json.addProperty("requestId", response.getRequestId());
        json.addProperty("result", response.getResult().name());
        json.addProperty("message", response.getMessage());
        if (response.getPayload() != null) {
            json.add("payload", response.getPayload());
        }
        return json;
    }
}
