package com.hydroline.beacon.provider.create;

import java.util.concurrent.atomic.AtomicReference;

public final class CreateQueryRegistry {
    private static final AtomicReference<CreateQueryGateway> GATEWAY = new AtomicReference<CreateQueryGateway>(CreateQueryGateway.UNAVAILABLE);

    private CreateQueryRegistry() {
    }

    public static CreateQueryGateway get() {
        return GATEWAY.get();
    }

    public static void register(CreateQueryGateway gateway) {
        GATEWAY.set(gateway == null ? CreateQueryGateway.UNAVAILABLE : gateway);
    }
}
