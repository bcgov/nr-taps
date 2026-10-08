package ca.bc.gov.nrs.taps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateRangeTest {
  @ParameterizedTest
  @ValueSource(strings = {"2024-02-29", "0001-01-01", "9999-12-31"})
  void parsesCalendarDatesWithinTheFourDigitYearContract(String input) {
    assertThat(DateRange.parse(input).toString()).isEqualTo(input);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025-02-29", "2026-04-31", "2026-13-01", "2026-01-00", "0000-01-01", "01/10/2026", "2026-1-1", "+10000-01-01", "2026-10-01T12:00:00"})
  void rejectsInvalidOrAmbiguousDates(String input) {
    assertThatIllegalArgumentException().isThrownBy(() -> DateRange.parse(input));
  }

  @Test
  void blanksAreOptionalAndWhitespaceDoesNotChangeTheDate() {
    assertThat(DateRange.parse(null)).isNull();
    assertThat(DateRange.parse("  ")).isNull();
    assertThat(DateRange.parse(" 2026-10-01 ")).isEqualTo(LocalDate.of(2026, 10, 1));
  }

  @Test
  void rangesAllowEqualAndOpenBounds() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    assertThat(new DateRange(date, date).hasBound()).isTrue();
    assertThat(new DateRange(date, null).hasBound()).isTrue();
    assertThat(new DateRange(null, date).hasBound()).isTrue();
    assertThat(new DateRange(null, null).hasBound()).isFalse();
  }

  @Test
  void rangesRejectReversedAndOutOfContractDates() {
    LocalDate date = LocalDate.of(2026, 10, 1);
    assertThatIllegalArgumentException().isThrownBy(() -> new DateRange(date, date.minusDays(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DateRange(LocalDate.of(0, 1, 1), null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new DateRange(null, LocalDate.of(10000, 1, 1)));
  }
}
