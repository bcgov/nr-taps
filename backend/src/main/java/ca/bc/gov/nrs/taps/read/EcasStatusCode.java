package ca.bc.gov.nrs.taps.read;

import java.time.LocalDateTime;

/** Inactive codes stay searchable; active is only used for display. */
public record EcasStatusCode(String code, String description, LocalDateTime effectiveDate,
    LocalDateTime expiryDate, LocalDateTime updateTimestamp, boolean active) {}
