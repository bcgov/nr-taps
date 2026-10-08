package ca.bc.gov.nrs.taps.read;

import java.time.LocalDateTime;

/** Includes inactive codes so older records can still be searched. */
public record DatedCodeOption(String code, String description, LocalDateTime effectiveDate,
    LocalDateTime expiryDate, boolean active) {}
