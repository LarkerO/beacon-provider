package com.hydroline.beacon.provider.transport;

import java.time.Instant;
import java.util.UUID;

/**
 * Transport-layer metadata describing the origin of an incoming request.
 */
public final class TransportContext {
    private final UUID originId;
    private final TransportKind kind;
    private final Instant receivedAt;

    public TransportContext(UUID originId, TransportKind kind, Instant receivedAt) {
        this.originId = originId;
        this.kind = kind;
        this.receivedAt = receivedAt;
    }

    public UUID getOriginId() {
        return originId;
    }

    public TransportKind getKind() {
        return kind;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
