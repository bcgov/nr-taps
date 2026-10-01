package ca.bc.gov.nrs.taps.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Proposed FAM region scope values and their corresponding region codes. GAS's archived
 * {@code ForestRegionCodes} includes these codes, and legacy record checks compare an appraisal's
 * administrative district or its {@code ORG_UNIT.ROLLUP_REGION_NO} with the user's org units.
 * Before querying live records, verify the name-to-code mapping against {@code ORG_UNIT}, resolve
 * district rollups from that table, and decide how retired RCO, RNI and RSI records should match.
 */
public enum FamRegion {
  CARIBOO("RCB"),
  KOOTENAY_BOUNDARY("RKB"),
  NORTHEAST("RNO"),
  OMINECA("ROM"),
  THOMPSON_OKANAGAN("RTO"),
  SKEENA("RSK"),
  SOUTH_COAST("RSC"),
  WEST_COAST("RWC");

  private final String orgUnitCode;

  FamRegion(String orgUnitCode) {
    this.orgUnitCode = orgUnitCode;
  }

  public static Optional<FamRegion> fromScopeValue(String value) {
    return Arrays.stream(values()).filter(region -> region.name().equals(value)).findFirst();
  }

  public static Optional<FamRegion> fromOrgUnitCode(String orgUnitCode) {
    return Arrays.stream(values())
        .filter(region -> region.orgUnitCode.equals(orgUnitCode))
        .findFirst();
  }

  public String orgUnitCode() {
    return orgUnitCode;
  }
}
