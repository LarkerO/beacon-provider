package com.hydroline.beacon.provider.create;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class CreateEdgeIdFactory {
    private CreateEdgeIdFactory() {
    }

    public static String build(String graphId,
                               int node1NetId,
                               int node2NetId,
                               boolean turn,
                               boolean portal,
                               double length,
                               String materialId) {
        String lengthToken = String.format(Locale.ROOT, "%.4f", length);
        String materialToken = materialId == null ? "" : materialId;
        String raw = graphId + ":" + node1NetId + ":" + node2NetId + ":" + turn + ":" + portal + ":" + lengthToken + ":" + materialToken;
        UUID uuid = UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
        return uuid.toString();
    }
}
