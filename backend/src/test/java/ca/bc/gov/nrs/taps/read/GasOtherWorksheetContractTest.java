package ca.bc.gov.nrs.taps.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class GasOtherWorksheetContractTest {
  @Test
  void rawRateComponentsSerializeAsExactDecimalStringsAndNullsWithoutInventedTotal() throws Exception {
    var rate = new GasAppraisal.StoredNonAppraisedRate("00042", "F", "01", "A",
        new BigDecimal("123.40"), null, BigDecimal.ZERO, new BigDecimal("-1.2"));
    var json = Jackson2ObjectMapperBuilder.json().build().valueToTree(rate);

    assertThat(json.get("rateId").asText()).isEqualTo("42");
    assertThat(json.get("reserveStumpageRate").isTextual()).isTrue();
    assertThat(json.get("reserveStumpageRate").asText()).isEqualTo("123.40");
    assertThat(json.get("bonusBidAmount").isNull()).isTrue();
    assertThat(json.get("developmentLevy").asText()).isEqualTo("0.00");
    assertThat(json.get("silvicultureLevy").asText()).isEqualTo("-1.20");
    assertThat(json.has("totalStumpageRate")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.234", "1000.00", "-1000.00"})
  void storedRateComponentsDoNotRoundOrExceedSchemaPrecision(String invalid) {
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", "F", "01", "A",
        new BigDecimal(invalid), null, null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", "F", "01", "A",
        BigDecimal.ZERO, new BigDecimal(invalid), null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void historicKeyAndVariantMustMatchStoredFamilyAndDispatch() {
    var key = new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "42");
    assertThatThrownBy(() -> new GasAppraisal.HistoricSummary(key, null, "ABC123", AppraisalMethod.C,
        GasAppraisal.SummaryVariant.COAST_MPS_TOA_Y, "MPS", false, true, true,
        "Policy", null, null, null, null, List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not match");
    var wrong = new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "42");
    assertThatThrownBy(() -> new GasAppraisal.HistoricSummary(wrong, null, "ABC123", AppraisalMethod.C,
        GasAppraisal.SummaryVariant.CVP, "CVP", false, true, true,
        "Policy", null, null, null, null, List.of(), List.of(), List.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("HISTORIC key");
  }

  @Test
  void nonAppraisedSummaryCopiesRatesAndRequiresItsFamily() {
    var key = new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "42");
    var rate = new GasAppraisal.StoredNonAppraisedRate("1", "F", "01", "A", BigDecimal.ZERO, null, null, null);
    var rates = new ArrayList<>(List.of(rate));
    var summary = new GasAppraisal.NonAppraisedSummary(key, null, "ABC123", AppraisalMethod.I,
        null, null, null, null, null, null, null, null, null, rates, List.of());
    rates.clear();
    assertThat(summary.rates()).containsExactly(rate);
    assertThatThrownBy(() -> summary.rates().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new GasAppraisal.NonAppraisedSummary(
        new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "42"), null, "ABC123", AppraisalMethod.I,
        null, null, null, null, null, null, null, null, null, rates, List.of()))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NON_APPRAISED key");
  }

  @Test
  void speciesNumbersRemainExactStringsAndNullableFieldsRemainNull() {
    var mapper = Jackson2ObjectMapperBuilder.json().build();
    var species = new GasAppraisal.HistoricSpecies("0042", "F", null, new BigDecimal("123.4500"),
        null, BigDecimal.ZERO, new BigDecimal("1.20"), null);
    var json = mapper.valueToTree(species);
    assertThat(json.get("speciesId").asText()).isEqualTo("42");
    assertThat(json.get("speciesVolume").isTextual()).isTrue();
    assertThat(json.get("speciesVolume").asText()).isEqualTo("123.4500");
    assertThat(json.get("lumberRecoveryFactor").isNull()).isTrue();
    assertThat(json.get("speciesDecayPercent").asText()).isEqualTo("0");
    assertThat(json.get("speciesStudPercent").asText()).isEqualTo("1.20");
    var grade = mapper.valueToTree(new GasAppraisal.HistoricCoastSpeciesGrade("43", "F", "02", "B", new BigDecimal("40.50")));
    assertThat(grade.get("scaleProductCode").asText()).isEqualTo("02");
    assertThat(grade.get("speciesGradePercent").asText()).isEqualTo("40.50");
  }
}
