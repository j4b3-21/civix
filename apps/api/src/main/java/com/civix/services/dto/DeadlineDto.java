package com.civix.services.dto;

/** {@code date} is {@code YYYY-MM-DD} or null (no fixed date); {@code verifiedAt} is the date it was checked. */
public record DeadlineDto(String date, String note, String sourceUrl, String verifiedAt) {
}
