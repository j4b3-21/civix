package com.civix.documents.service;

import java.util.UUID;

/** Published inside the upload / re-extract transaction; handled asynchronously after commit. */
public record DocumentExtractionRequested(UUID documentId, UUID userId) {
}
