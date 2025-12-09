package com.hydroline.beacon.provider.gateway;

public enum GatewayMessageType {
    HANDSHAKE("handshake"),
    HANDSHAKE_ACK("handshake_ack"),
    REQUEST("request"),
    RESPONSE("response"),
    PING("ping"),
    PONG("pong"),
    ERROR("error");

    private final String wireName;

    GatewayMessageType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static GatewayMessageType fromWireName(String wireName) {
        for (GatewayMessageType type : values()) {
            if (type.wireName.equalsIgnoreCase(wireName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown gateway message type: " + wireName);
    }
}
