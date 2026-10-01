package ca.bc.gov.nrs.taps.security;

import java.util.Arrays;
import java.util.Optional;

/** Verify rollup codes against ORG_UNIT when implementing database access. */
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
