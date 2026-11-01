package com.civix.common.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.civix.config.CivixProperties;

/** Ensures the private bucket exists once the application is up. Failures are logged, never fatal. */
@Component
public class StorageInitializer {

    private static final Logger log = LoggerFactory.getLogger(StorageInitializer.class);

    private final ObjectStorageService storage;
    private final boolean enabled;

    public StorageInitializer(ObjectStorageService storage, CivixProperties props) {
        this.storage = storage;
        this.enabled = props.storage().initBucket();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!enabled) {
            return;
        }
        Thread.ofVirtual().name("storage-init").start(() -> {
            if (!storage.ensureBucket()) {
                log.warn("Object storage is not available yet; uploads will fail until it is reachable");
            }
        });
    }
}
