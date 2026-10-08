package ca.bc.gov.nrs.taps.read;

import java.time.LocalDateTime;

/** A GAS code-list row as stored. Dates keep their time of day and have no time zone. */
public record EffectiveCode(
    String code,
    String description,
    LocalDateTime effectiveDate,
    LocalDateTime expiryDate,
    LocalDateTime updateTimestamp) {}
