package com.civix.privacy.dto;

public record DeletionResultDto(String scope, int deletedDocuments, int deletedProfileFields, boolean accountDeleted) {
}
