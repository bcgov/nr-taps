package ca.bc.gov.nrs.taps.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class GasOtherWorksheetContractTest {
  @Test
  void labelsAndRawComponentsSerializeWithExactDerivedDecimalStrings() throws Exception {
    var rate = new GasAppraisal.StoredNonAppraisedRate("00042", new CodeOption("F", "Synthetic fir"),
        new CodeOption(" ", "Logs"), new CodeOption(" ", "Ungraded"),
        new BigDecimal("123.40"), null, BigDecimal.ZERO, new BigDecimal("-1.2"));
    var json = Jackson2ObjectMapperBuilder.json().build().valueToTree(rate);

    assertThat(json.get("rateId").asText()).isEqualTo("42");
    assertThat(json.get("reserveStumpageRate").isTextual()).isTrue();
    assertThat(json.get("reserveStumpageRate").asText()).isEqualTo("123.40");
    assertThat(json.get("bonusBidAmount").isNull()).isTrue();
    assertThat(json.get("developmentLevy").asText()).isEqualTo("0.00");
    assertThat(json.get("silvicultureLevy").asText()).isEqualTo("-1.20");
    assertThat(json.get("scaleSpecies").get("description").asText()).isEqualTo("Synthetic fir");
    assertThat(json.get("scaleProduct").get("code").asText()).isEqualTo(" ");
    assertThat(json.get("scaleGrade").get("code").asText()).isEqualTo(" ");
    assertThat(json.get("upsetStumpageRate").isTextual()).isTrue();
    assertThat(json.get("upsetStumpageRate").asText()).isEqualTo("122.20");
    assertThat(json.get("totalStumpageRate").isTextual()).isTrue();
    assertThat(json.get("totalStumpageRate").asText()).isEqualTo("122.20");
    assertThat(json.has("scaleSpeciesCode")).isFalse();
    assertThat(json.has("scaleProductCode")).isFalse();
    assertThat(json.has("scaleGradeCode")).isFalse();
  }

  @ParameterizedTest
  @CsvSource(nullValues = "NULL", value = {
      "0,NULL,NULL,NULL,0.00,0.00",
      "1.25,0,0,0,1.25,1.25",
      "1.10,2.20,3.30,4.40,8.80,11.00",
      "5.10,NULL,0,-1.20,3.90,3.90",
      "0,50.25,NULL,NULL,0.00,50.25",
      "999.99,999.99,999.99,999.99,2999.97,3999.96",
      "-999.99,-999.99,-999.99,-999.99,-2999.97,-3999.96"
  })
  void totalsAddOnlyStoredNonNullComponentsWithoutExtraRoundingOrCap(BigDecimal reserve,
      BigDecimal bonus, BigDecimal development, BigDecimal silviculture, String upset, String total) {
    var rate = new GasAppraisal.StoredNonAppraisedRate("42", new CodeOption("F", null),
        new CodeOption("01", null), new CodeOption("A", null), reserve, bonus, development, silviculture);
    assertThat(rate.upsetStumpageRate()).isEqualTo(new BigDecimal(upset));
    assertThat(rate.totalStumpageRate()).isEqualTo(new BigDecimal(total));
    assertThat(rate.bonusBidAmount() == null).isEqualTo(bonus == null);
    assertThat(rate.developmentLevy() == null).isEqualTo(development == null);
    assertThat(rate.silvicultureLevy() == null).isEqualTo(silviculture == null);
  }

  @Test
  void suppliedDisplayTotalsCannotOverrideTheStoredComponents() throws Exception {
    var mapper = Jackson2ObjectMapperBuilder.json().build();
    var rate = mapper.readValue("""
        {"rateId":"42","scaleSpecies":{"code":"F","description":null},
         "scaleProduct":{"code":" ","description":"Logs"},
         "scaleGrade":{"code":" ","description":"Ungraded"},
         "reserveStumpageRate":"1.20","bonusBidAmount":"0.03",
         "developmentLevy":null,"silvicultureLevy":null,
         "upsetStumpageRate":"9000.00","totalStumpageRate":"9001.00"}
        """, GasAppraisal.StoredNonAppraisedRate.class);
    assertThat(rate.upsetStumpageRate()).isEqualTo(new BigDecimal("1.20"));
    assertThat(rate.totalStumpageRate()).isEqualTo(new BigDecimal("1.23"));
    JsonNode json = mapper.valueToTree(rate);
    assertThat(mapper.treeToValue(json, GasAppraisal.StoredNonAppraisedRate.class)).isEqualTo(rate);
    assertThat(json.get("upsetStumpageRate").asText()).isEqualTo("1.20");
    assertThat(json.get("totalStumpageRate").asText()).isEqualTo("1.23");
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.234", "1000.00", "-1000.00"})
  void storedRateComponentsDoNotRoundOrExceedSchemaPrecision(String invalid) {
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", new CodeOption("F", null),
        new CodeOption("01", null), new CodeOption("A", null),
        new BigDecimal(invalid), null, null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", new CodeOption("F", null),
        new CodeOption("01", null), new CodeOption("A", null),
        BigDecimal.ZERO, new BigDecimal(invalid), null, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reserveAndSelectedScaleCodesRemainRequired() {
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", new CodeOption("F", null),
        new CodeOption(" ", null), new CodeOption(" ", null), null, null, null, null))
        .isInstanceOf(NullPointerException.class).hasMessage("reserveStumpageRate");
    assertThatThrownBy(() -> new GasAppraisal.StoredNonAppraisedRate("42", new CodeOption(null, null),
        new CodeOption(" ", null), new CodeOption(" ", null), BigDecimal.ZERO, null, null, null))
        .isInstanceOf(NullPointerException.class).hasMessage("scaleSpecies.code");
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
    var rate = new GasAppraisal.StoredNonAppraisedRate("1", new CodeOption("F", null),
        new CodeOption("01", null), new CodeOption("A", null), BigDecimal.ZERO, null, null, null);
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
