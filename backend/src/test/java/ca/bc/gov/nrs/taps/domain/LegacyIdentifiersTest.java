package ca.bc.gov.nrs.taps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LegacyIdentifiersTest {
  @ParameterizedTest
  @CsvSource({"1,1", "00042,42", "999999999999,999999999999", "000000000001,1"})
  void numericIdsArePositiveAndCanonical(String input, String expected) {
    assertThat(LegacyIdentifiers.requiredId(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0000", "-1", "+1", "1.0", "1e2", "1000000000000", "1 2", "１２", "1'"})
  void invalidIdsAreRejected(String input) {
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.requiredId(input));
  }

  @Test
  void emptyOptionalIdsAndMissingRequiredIdsStayDistinct() {
    assertThat(LegacyIdentifiers.optionalId(null)).isNull();
    assertThat(LegacyIdentifiers.optionalId("  ")).isNull();
    assertThat(LegacyIdentifiers.requiredId(" 00042 ")).isEqualTo("42");
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.requiredId(null));
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.requiredId(" "));
  }

  @ParameterizedTest
  @CsvSource({"1,00000001", "99990001,99990001", "00000001,00000001", "0,00000000"})
  void numericClientSelectionsRetainLeadingZeroes(String input, String expected) {
    assertThat(LegacyIdentifiers.clientNumber(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"100000000", "-1", "+1", "1.5", "ACME", "1 2"})
  void invalidNumericClientSelectionsNeedTheSeparateLookup(String input) {
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.clientNumber(input));
  }

  @Test
  void locationPaddingDoesNotInventNumericOnlyMembership() {
    assertThat(LegacyIdentifiers.clientLocationCode("1")).isEqualTo("01");
    assertThat(LegacyIdentifiers.clientLocationCode("A")).isEqualTo("0A");
    assertThat(LegacyIdentifiers.clientLocationCode("01")).isEqualTo("01");
    assertThat(LegacyIdentifiers.clientLocationCode("  ")).isNull();
    assertThat(LegacyIdentifiers.clientNumber(null)).isNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> LegacyIdentifiers.clientLocationCode("001"));
  }

  @Test
  void alphanumericFieldsRespectSourceWidths() {
    assertThat(LegacyIdentifiers.licence(" abc1234567 ")).isEqualTo("ABC1234567");
    assertThat(LegacyIdentifiers.cuttingPermit("a01")).isEqualTo("A01");
    assertThat(LegacyIdentifiers.managementUnitType("t")).isEqualTo("T");
    assertThat(LegacyIdentifiers.managementUnitId("1234")).isEqualTo("1234");
    assertThat(LegacyIdentifiers.licence(" ")).isNull();
    assertThat(LegacyIdentifiers.cuttingPermit(null)).isNull();
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.licence("ABC12345678"));
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.cuttingPermit("A001"));
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.managementUnitType("TT"));
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.managementUnitId("12345"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"A-1", "A 1", "A'1", "A_1"})
  void ecasAlphanumericValidationMatchesTheSharedValidator(String input) {
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.licence(input));
  }

  @Test
  void caseNormalizationIsIndependentOfTheHostLocale() {
    Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      assertThat(LegacyIdentifiers.licence("i123")).isEqualTo("I123");
      assertThat(LegacyIdentifiers.licence("é12")).isEqualTo("É12");
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void textIsBoundedWithoutInventingAnAlphanumericConstraint() {
    assertThat(LegacyIdentifiers.timberMark(" a-1 ")).isEqualTo("A-1");
    assertThat(LegacyIdentifiers.timberMark("abcdef")).isEqualTo("ABCDEF");
    assertThat(LegacyIdentifiers.timberMark(" ")).isNull();
    assertThatIllegalArgumentException().isThrownBy(() -> LegacyIdentifiers.timberMark("ABCDEFG"));
  }
}
