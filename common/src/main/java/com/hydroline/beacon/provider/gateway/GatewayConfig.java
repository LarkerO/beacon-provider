package com.hydroline.beacon.provider.gateway;

import java.util.Objects;

public final class GatewayConfig {
    private final String listenAddress;
    private final int listenPort;
    private final String authToken;
    private final int handshakeTimeoutSeconds;
    private final int idleTimeoutSeconds;

    private GatewayConfig(Builder builder) {
        this.listenAddress = builder.listenAddress;
        this.listenPort = builder.listenPort;
        this.authToken = builder.authToken;
        this.handshakeTimeoutSeconds = builder.handshakeTimeoutSeconds;
        this.idleTimeoutSeconds = builder.idleTimeoutSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static GatewayConfig defaults() {
        return builder()
            .listenAddress("127.0.0.1")
            .listenPort(28545)
            .authToken("change-me")
            .handshakeTimeoutSeconds(10)
            .idleTimeoutSeconds(240)
            .build();
    }

    public String listenAddress() {
        return listenAddress;
    }

    public int listenPort() {
        return listenPort;
    }

    public String authToken() {
        return authToken;
    }

    public int handshakeTimeoutSeconds() {
        return handshakeTimeoutSeconds;
    }

    public int idleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public static final class Builder {
        private String listenAddress;
        private int listenPort;
        private String authToken;
        private int handshakeTimeoutSeconds;
        private int idleTimeoutSeconds;

        private Builder() {
        }

        public Builder listenAddress(String listenAddress) {
            this.listenAddress = Objects.requireNonNull(listenAddress, "listenAddress");
            return this;
        }

        public Builder listenPort(int listenPort) {
            this.listenPort = listenPort;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = Objects.requireNonNull(authToken, "authToken");
            return this;
        }

        public Builder handshakeTimeoutSeconds(int seconds) {
            this.handshakeTimeoutSeconds = seconds;
            return this;
        }

        public Builder idleTimeoutSeconds(int seconds) {
            this.idleTimeoutSeconds = seconds;
            return this;
        }

        public GatewayConfig build() {
            if (listenAddress == null) {
                throw new IllegalStateException("listenAddress not set");
            }
            if (listenPort < 0 || listenPort > 65535) {
                throw new IllegalStateException("listenPort must be between 0 and 65535");
            }
            if (authToken == null || authToken.isEmpty()) {
                throw new IllegalStateException("authToken must not be empty");
            }
            if (handshakeTimeoutSeconds <= 0) {
                throw new IllegalStateException("handshakeTimeoutSeconds must be > 0");
            }
            if (idleTimeoutSeconds <= 0) {
                throw new IllegalStateException("idleTimeoutSeconds must be > 0");
            }
            return new GatewayConfig(this);
        }
    }
}
