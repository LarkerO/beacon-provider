package com.hydroline.beacon.provider.create;

import java.util.Optional;

public interface CreateQueryGateway {
    boolean isReady();

    Optional<CreateNetworkSnapshot> fetchNetworkSnapshot(String graphId, boolean includePolylines);

    CreateRealtimeSnapshot fetchRealtimeSnapshot();

    CreateQueryGateway UNAVAILABLE = new CreateQueryGateway() {
        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public Optional<CreateNetworkSnapshot> fetchNetworkSnapshot(String graphId, boolean includePolylines) {
            return Optional.empty();
        }

        @Override
        public CreateRealtimeSnapshot fetchRealtimeSnapshot() {
            return CreateRealtimeSnapshot.empty();
        }
    };
}
