package ca.bc.gov.nrs.taps.security;

/**
 * Where a submission or appraisal belongs, as loaded from the database: its administrative district
 * code (for example {@code DCC}), that district's rollup region code (for example {@code RCB}) and
 * its forest client number. Any of them may be null; a null never matches a scoped grant.
 */
public record RecordScope(String adminDistrictCode, String rollupRegionCode, String clientNumber) {}
