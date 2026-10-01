package ca.bc.gov.nrs.taps.security;

/** Database ownership fields; missing values never match scoped grants. */
public record RecordScope(String adminDistrictCode, String rollupRegionCode, String clientNumber) {}
