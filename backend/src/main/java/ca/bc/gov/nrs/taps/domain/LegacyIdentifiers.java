package ca.bc.gov.nrs.taps.domain;

import java.util.Locale;

/** Format checks only; the reads check existence and access. */
public final class LegacyIdentifiers {
  private LegacyIdentifiers() {}

  public static String requiredId(String value) {
    String id = optionalId(value);
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
    return id;
  }

  public static String optionalId(String value) {
    String id = optionalText(value, 12, "id");
    if (id == null) {
      return null;
    }
    if (!id.matches("[0-9]{1,12}") || Long.parseLong(id) == 0) {
      throw new IllegalArgumentException("id must be a positive integer of at most 12 digits");
    }
    return Long.toString(Long.parseLong(id));
  }

  public static String licence(String value) {
    return alphaNumeric(value, 10, "licence");
  }

  public static String cuttingPermit(String value) {
    return alphaNumeric(value, 3, "cuttingPermit");
  }

  public static String timberMark(String value) {
    String mark = optionalText(value, 6, "timberMark");
    return mark == null ? null : optionalText(mark.toUpperCase(Locale.ROOT), 6, "timberMark");
  }

  public static String managementUnitType(String value) {
    return alphaNumeric(value, 1, "managementUnitType");
  }

  public static String managementUnitId(String value) {
    return alphaNumeric(value, 4, "managementUnitId");
  }

  /** Client numbers only; acronym search isn't ported yet. */
  public static String clientNumber(String value) {
    return paddedDigits(value, 8, "clientNumber");
  }

  public static String clientLocationCode(String value) {
    String location = optionalText(value, 2, "clientLocationCode");
    return location == null ? null : "0".repeat(2 - location.length()) + location;
  }

  public static String optionalText(String value, int maxLength, String field) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    String text = value.trim();
    if (text.length() > maxLength) {
      throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
    }
    return text;
  }

  private static String alphaNumeric(String value, int maxLength, String field) {
    String text = optionalText(value, maxLength, field);
    if (text == null) {
      return null;
    }
    if (!text.chars().allMatch(c -> Character.isLetterOrDigit((char) c))) {
      throw new IllegalArgumentException(field + " must contain letters or digits only");
    }
    return optionalText(text.toUpperCase(Locale.ROOT), maxLength, field);
  }

  private static String paddedDigits(String value, int length, String field) {
    String text = optionalText(value, length, field);
    if (text == null) {
      return null;
    }
    if (!text.matches("[0-9]+")) {
      throw new IllegalArgumentException(field + " must contain digits only");
    }
    return "0".repeat(length - text.length()) + text;
  }
}
