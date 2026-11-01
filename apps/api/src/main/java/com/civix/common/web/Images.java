package com.civix.common.web;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.civix.common.storage.ObjectStorageService.StoredObject;

/** Streams a private PNG (screenshots, receipt artifacts) to its owner without caching. */
public final class Images {

    private Images() {
    }

    public static ResponseEntity<InputStreamResource> png(StoredObject object) {
        ResponseEntity.BodyBuilder b = ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff");
        if (object.contentLength() != null) {
            b.contentLength(object.contentLength());
        }
        return b.body(new InputStreamResource(object.content()));
    }
}
