package com.civix.common.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.civix.common.storage.ObjectStorageService;

/** Reports object-storage reachability as a detail; always UP so readiness does not flap on storage hiccups. */
@Component("storageHealthIndicator")
public class StorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageService storage;

    public StorageHealthIndicator(ObjectStorageService storage) {
        this.storage = storage;
    }

    @Override
    public Health health() {
        return Health.up().withDetail("reachable", storage.isReachable()).build();
    }
}
