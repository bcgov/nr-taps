package ca.bc.gov.nrs.taps.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ReadContractsTest {
  private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

  @ParameterizedTest
  @MethodSource("workflowShapes")
  void syntheticWorkflowShapesRoundTripWithIsoDatesAndStringIds(String name, Class<?> type)
      throws IOException {
    JsonNode json = fixture().get(name);
    Object value = mapper.treeToValue(json, type);
    assertThat(mapper.readTree(mapper.writeValueAsBytes(value))).isEqualTo(json);
  }

  static Stream<Arguments> workflowShapes() {
    return Stream.of(
        Arguments.of("ecasSearch", EcasInbox.Search.class),
        Arguments.of("ecasInboxItem", EcasInbox.Item.class),
        Arguments.of("ecasCoastReference", EcasReference.Coast.class),
        Arguments.of("ecasInteriorReference", EcasReference.Interior.class),
        Arguments.of("gasSearch", GasAppraisal.Search.class),
        Arguments.of("gasSearchPage", GasAppraisal.Page.class),
        Arguments.of("gasAppraisedSummary", GasAppraisal.AppraisedSummary.class),
        Arguments.of("gasLicenceMarks", GasAppraisal.LicenceMarks.class),
        Arguments.of("gasSearchResult", GasAppraisal.SearchResult.class),
        Arguments.of("gasSearchResultWithoutAppraisals", GasAppraisal.SearchResult.class),
        Arguments.of("ecasInboxMultiMarkItems", EcasInbox.Item[].class),
        Arguments.of("ecasCoastMultiMarkReference", EcasReference.Coast.class),
        Arguments.of("gasMultiMarkAppraisedSummary", GasAppraisal.AppraisedSummary.class));
  }

  @Test
  void ecasDefaultsDoNotInferAClientOrAppraisalLocation() throws IOException {
    EcasInbox.Search search = search("{}");
    assertThat(search.mode()).isEqualTo(EcasInbox.Mode.MY_TO_DO);
    assertThat(search.sortBy()).isEqualTo(EcasInbox.SortField.ECAS_ID);
    assertThat(search.sortDirection()).isEqualTo(EcasInbox.SortDirection.DESC);
    assertThat(search.clientNumber()).isNull();
    assertThat(search.appraisalMethod()).isNull();
    assertThat(search.orgUnitNumbers()).isEmpty();
    assertThat(search.statusCodes()).isEmpty();
    assertThat(search.dateTypes()).isEmpty();
  }

  @Test
  void ecasFilterValuesAreNormalizedAndNeverBecomeScope() throws IOException {
    EcasInbox.Search search =
        search("""
            {"licence":" x99999 ","cuttingPermit":"a01","timberMark":" zz9999 ","clientNumber":"1",
             "clientLocationCode":"1","managementUnitType":"t","managementUnitId":"1234",
             "orgUnitNumbers":[" 999 ","000999",""],"statusCodes":["","SUB"],
             "appraisalCategoryCode":" ","reappraisalReasonCode":" RED ","fileTypeCode":""}
            """);
    assertThat(search.licence()).isEqualTo("X99999");
    assertThat(search.cuttingPermit()).isEqualTo("A01");
    assertThat(search.timberMark()).isEqualTo("ZZ9999");
    assertThat(search.clientNumber()).isEqualTo("00000001");
    assertThat(search.clientLocationCode()).isEqualTo("01");
    assertThat(search.managementUnitType()).isEqualTo("T");
    assertThat(search.orgUnitNumbers()).containsExactly("999");
    assertThat(search.statusCodes()).containsExactly("SUB");
    assertThat(search.appraisalCategoryCode()).isNull();
    assertThat(search.reappraisalReasonCode()).isEqualTo("RED");
    assertThat(search.fileTypeCode()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"cuttingPermit\":\"001\"}",
    "{\"managementUnitId\":\"1234\"}",
    "{\"clientLocationCode\":\"00\"}",
    "{\"workedOnByUserId\":\"1234567890123456789012345678901\"}",
    "{\"timberMark\":\"ZZ99999\"}",
    "{\"orgUnitNumbers\":[\"bogus\"]}",
    "{\"orgUnitNumbers\":[\"-1\"]}",
    "{\"orgUnitNumbers\":[\"0\"]}",
    "{\"orgUnitNumbers\":[\"1.5\"]}",
    "{\"orgUnitNumbers\":[\"1234567890123\"]}",
    "{\"dates\":{\"from\":\"2026-10-01\"}}",
    "{\"statusDates\":{\"to\":\"2026-10-01\"},\"statusCodes\":[\"\"]}",
    "{\"statusDates\":{\"from\":\"2026-10-01\"},\"statusCodes\":[\"SUB\",\"EE\"]}",
    "{\"dateTypes\":[\"EFFCTV\"],\"dates\":{\"from\":\"2026-10-02\",\"to\":\"2026-10-01\"}}"
  })
  void invalidDependentFiltersFailDuringConstruction(String json) {
    assertThatThrownBy(() -> search(json)).hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void openDateBoundsRequireTheCorrespondingSelection() throws IOException {
    EcasInbox.Search search =
        search("""
            {"dateTypes":["EFFCTV","EXPRY"],"dates":{"from":"2026-10-01"},
             "statusCodes":["SUB"],"statusDates":{"to":"2026-10-01"}}
            """);
    assertThat(search.dates().to()).isNull();
    assertThat(search.statusDates().from()).isNull();
    assertThat(search.dateTypes()).containsExactly(EcasInbox.DateType.EFFCTV, EcasInbox.DateType.EXPRY);
    assertThat(search("{\"statusCodes\":[\"EE\"]}").statusCodes()).containsExactly("EE");
  }

  @Test
  void duplicateDateSelectionsCannotGenerateRepeatedSqlPredicates() throws IOException {
    var repeated = java.util.Collections.nCopies(500, "\"EFFCTV\"");
    EcasInbox.Search search = search("{\"dateTypes\":[" + String.join(",", repeated)
        + ",\"EXPRY\"],\"dates\":{\"from\":\"2026-10-01\"}}");
    assertThat(search.dateTypes()).containsExactly(EcasInbox.DateType.EFFCTV, EcasInbox.DateType.EXPRY);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "\"2026-10-01T12:00:00\"",
    "[2026,10,1]",
    "20727",
    "true",
    "{}",
    "\"2025-02-29\"",
    "\"0000-01-01\"",
    "\"2026-1-1\"",
    "\"+10000-01-01\""
  })
  void filterDatesUseTheStrictParserAtTheJsonBoundary(String value) {
    String json = "{\"dateTypes\":[\"EFFCTV\"],\"dates\":{\"from\":" + value + "}}";
    assertThatThrownBy(() -> search(json)).isInstanceOf(JsonMappingException.class);
  }

  @Test
  void referenceVariantsRejectTheOtherAppraisalMethod() throws IOException {
    ObjectNode coast = (ObjectNode) fixture().get("ecasCoastReference");
    ((ObjectNode) coast.get("header")).put("appraisalMethod", "I");
    assertThatThrownBy(() -> mapper.treeToValue(coast, EcasReference.Coast.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    ObjectNode interior = (ObjectNode) fixture().get("ecasInteriorReference");
    ((ObjectNode) interior.get("header")).put("appraisalMethod", "C");
    assertThatThrownBy(() -> mapper.treeToValue(interior, EcasReference.Interior.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ecasCoastReference", "ecasInteriorReference"})
  void standRateEligibilitySerializesCodesAndDescriptionsSeparately(String name) throws IOException {
    EcasReference.Header header = mapper.treeToValue(fixture().get(name).get("header"),
        EcasReference.Header.class);
    assertThat(header.coniferousStandRateEligibility())
        .isEqualTo(new CodeOption("S", "Sawlog Grades"));
    assertThat(header.deciduousStandRateEligibility())
        .isEqualTo(new CodeOption("N", "No Grades"));
    ObjectNode json = mapper.valueToTree(header);
    assertThat(json.get("coniferousStandRateEligibility").get("description").asText())
        .isEqualTo("Sawlog Grades");
    assertThat(json.get("deciduousStandRateEligibility").get("code").asText()).isEqualTo("N");
  }

  @Test
  void referenceContractPreservesAbsentAndUnlabelledStandRateEligibility() throws IOException {
    ObjectNode json = (ObjectNode) fixture().get("ecasInteriorReference").get("header");
    json.putNull("coniferousStandRateEligibility");
    json.set("deciduousStandRateEligibility", mapper.readTree("{\"code\":\"X\",\"description\":null}"));
    EcasReference.Header header = mapper.treeToValue(json, EcasReference.Header.class);
    assertThat(header.coniferousStandRateEligibility()).isNull();
    assertThat(header.deciduousStandRateEligibility()).isEqualTo(new CodeOption("X", null));
    JsonNode serialized = mapper.valueToTree(header);
    assertThat(serialized).isEqualTo(json);
  }

  @ParameterizedTest
  @CsvSource({"0,APPRAISED", "1,NON_APPRAISED", "2,HISTORIC"})
  void worksheetTypesPreserveTheLegacyDiscriminator(int code, GasAppraisal.WorksheetType type) {
    assertThat(GasAppraisal.WorksheetType.fromLegacyCode(code)).isEqualTo(type);
  }

  @Test
  void unknownWorksheetTypesAndSameNumberedKeysStayDistinct() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GasAppraisal.WorksheetType.fromLegacyCode(3));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> GasAppraisal.WorksheetType.fromLegacyCode(-1));
    assertThat(new GasAppraisal.Key(GasAppraisal.WorksheetType.APPRAISED, "42"))
        .isNotEqualTo(new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "42"));
  }

  @ParameterizedTest
  @CsvSource({
    "CVP,C,true,CVP",
    "CVP,I,false,CVP",
    "MPS,I,,INTERIOR_MPS",
    "MPS,C,true,COAST_MPS_TOA_Y",
    "MPS,C,false,COAST_MPS_TOA_N"
  })
  void summaryVariantsMatchTheSourceDispatcher(
      String rateMethod, AppraisalMethod method, Boolean toa, GasAppraisal.SummaryVariant expected) {
    assertThat(GasAppraisal.SummaryVariant.resolve(rateMethod, method, toa)).contains(expected);
  }

  @Test
  void incompleteOrUnknownSummaryInputsDoNotSelectAVariant() {
    assertThat(GasAppraisal.SummaryVariant.resolve("MPS", AppraisalMethod.C, null)).isEmpty();
    assertThat(GasAppraisal.SummaryVariant.resolve("MPS", null, true)).isEmpty();
    assertThat(GasAppraisal.SummaryVariant.resolve("mps", AppraisalMethod.I, false)).isEmpty();
    assertThat(GasAppraisal.SummaryVariant.resolve(null, AppraisalMethod.I, false)).isEmpty();
  }

  @Test
  void appraisedSummariesRejectOtherFamiliesAndInconsistentVariants() throws IOException {
    ObjectNode otherFamily = (ObjectNode) fixture().get("gasAppraisedSummary");
    ((ObjectNode) otherFamily.get("key")).put("type", "NON_APPRAISED");
    assertThatThrownBy(() -> mapper.treeToValue(otherFamily, GasAppraisal.AppraisedSummary.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    ObjectNode mismatched = (ObjectNode) fixture().get("gasAppraisedSummary");
    mismatched.put("variant", "COAST_MPS_TOA_Y");
    assertThatThrownBy(() -> mapper.treeToValue(mismatched, GasAppraisal.AppraisedSummary.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gasFiltersAndPagesPreserveTheirSeparateSemantics() {
    GasAppraisal.Search search = new GasAppraisal.Search(" x99999 ", " zz9999 ", null);
    assertThat(search.licence()).isEqualTo("X99999");
    assertThat(search.timberMark()).isEqualTo("ZZ9999");
    assertThat(search.page()).isZero();
    assertThat(new GasAppraisal.Search("X-1", null, 0).licence()).isEqualTo("X-1");
    assertThat(new GasAppraisal.Search(null, "ZZ9999", 0).licence()).isNull();
    assertThat(new GasAppraisal.Search("", "", 0).timberMark()).isNull();
    assertThat(new GasAppraisal.Search(null, null, Integer.MAX_VALUE).page()).isEqualTo(Integer.MAX_VALUE);
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Search(null, null, -1));
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Search("X1234567890", null, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Search(null, "ZZ99999", 0));
    assertThat(new GasAppraisal.Page(List.of(), 0, 0).items()).isEmpty();
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Page(List.of(), -1, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Page(List.of(), 0, -1));
  }

  @Test
  void pagesCopyTheirItemsAndEnforceTheFixedSize() throws IOException {
    GasAppraisal.Item item =
        mapper.treeToValue(fixture().get("gasSearchPage").get("items").get(0), GasAppraisal.Item.class);
    List<GasAppraisal.Item> items = new ArrayList<>(List.of(item));
    GasAppraisal.Page page = new GasAppraisal.Page(items, 1, 0);
    items.clear();
    assertThat(page.items()).containsExactly(item);
    assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatIllegalArgumentException().isThrownBy(() -> new GasAppraisal.Page(List.of(item), 0, 0));
    List<GasAppraisal.Item> ten = java.util.Collections.nCopies(10, item);
    assertThat(new GasAppraisal.Page(ten, 10, 0).items()).hasSize(10);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new GasAppraisal.Page(java.util.Collections.nCopies(11, item), 11, 0));
  }

  @Test
  void licenceMarkChoicesPreserveOrderAndAnEmptyResult() throws IOException {
    List<String> marks = new ArrayList<>(List.of("ZZ9998", "ZZ9997", "ZZ-996"));
    GasAppraisal.LicenceMarks choices = new GasAppraisal.LicenceMarks("X99998", marks);
    marks.clear();
    assertThat(choices.timberMarks()).containsExactly("ZZ9998", "ZZ9997", "ZZ-996");
    assertThatThrownBy(() -> choices.timberMarks().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(mapper.readValue("""
        {"licence":"X99998","timberMarks":[]}
        """, GasAppraisal.LicenceMarks.class).timberMarks()).isEmpty();
  }

  @Test
  void ftaContextRemainsAvailableWithoutAnyWorksheets() throws IOException {
    GasAppraisal.SearchResult result = mapper.treeToValue(
        fixture().get("gasSearchResultWithoutAppraisals"), GasAppraisal.SearchResult.class);
    assertThat(result.appraisals().items()).isEmpty();
    assertThat(result.appraisals().total()).isZero();
    assertThat(result.licenceInformation().timberMark()).isEqualTo("ZZ9996");
    assertThat(result.licenceInformation().clientNumber()).isEqualTo("99990001");
    assertThat(result.licenceInformation().cuttingPermit()).isNull();
    assertThat(result.licenceInformation().markExpiryDate()).isEqualTo(LocalDate.of(2027, 9, 30));
    assertThat(result.licenceInformation().markExtendDate()).isNull();
  }

  @Test
  void missingFtaContextDoesNotDiscardWorksheetRows() throws IOException {
    ObjectNode json = (ObjectNode) fixture().get("gasSearchResult");
    json.putNull("licenceInformation");
    GasAppraisal.SearchResult result = mapper.treeToValue(json, GasAppraisal.SearchResult.class);
    assertThat(result.licenceInformation()).isNull();
    assertThat(result.appraisals().items()).hasSize(2);
    assertThat(mapper.readTree(mapper.writeValueAsBytes(result))).isEqualTo(json);
  }

  @ParameterizedTest
  @ValueSource(strings = {"002,003", "002, 003"})
  void ftaDisplayValuesPreserveAggregatedPermitsAndUnknownDates(String permits) throws IOException {
    ObjectNode json = (ObjectNode) mapper.readTree("""
        {"clientNumber":"00000001","licenseeName":"Mixed case name","licenceNumber":"X-001",
         "cuttingPermit":null,"fileTypeCode":null,"timberMark":null,"forestRegion":null,
         "forestDistrict":null,"markExpiryDate":null,"markExtendDate":null,"ftaStatus":null,
         "markStatus":null,"cruiseBased":null}
        """);
    json.put("cuttingPermit", permits);
    GasAppraisal.FtaLicenceInformation info =
        mapper.treeToValue(json, GasAppraisal.FtaLicenceInformation.class);
    assertThat(info.clientNumber()).isEqualTo("00000001");
    assertThat(info.cuttingPermit()).isEqualTo(permits);
    assertThat(info.markExpiryDate()).isNull();
    assertThat(info.markExtendDate()).isNull();
    assertThat(info.markStatus()).isNull();
    assertThat(info.cruiseBased()).isNull();
    assertThat(mapper.readTree(mapper.writeValueAsBytes(info))).isEqualTo(json);
  }

  @Test
  void searchLicenceStatusRemainsSeparateFromSummaryMarkStatusAndNullableCruise() throws IOException {
    GasAppraisal.SearchResult result = mapper.treeToValue(fixture().get("gasSearchResult"), GasAppraisal.SearchResult.class);
    assertThat(result.licenceInformation().ftaStatus()).isEqualTo("Synthetic active licence");
    assertThat(result.licenceInformation().markStatus())
        .isEqualTo(new CodeOption("I", "Synthetic issued mark"));
    assertThat(result.licenceInformation().cruiseBased()).isTrue();
    JsonNode json = mapper.valueToTree(result.licenceInformation());
    assertThat(json.get("markStatus").get("code").asText()).isEqualTo("I");
    assertThat(json.get("cruiseBased").isBoolean()).isTrue();
    GasAppraisal.SearchResult other = mapper.treeToValue(fixture().get("gasSearchResultWithoutAppraisals"), GasAppraisal.SearchResult.class);
    assertThat(other.licenceInformation().cruiseBased()).isNull();
  }

  @Test
  void summaryPrimaryMarkRemainsOptionalAndIndependentOfMarksOrder() throws IOException {
    ObjectNode json = (ObjectNode) fixture().get("gasMultiMarkAppraisedSummary");
    json.put("primaryTimberMark", "ZZ9997");
    GasAppraisal.AppraisedSummary summary = mapper.treeToValue(json, GasAppraisal.AppraisedSummary.class);
    assertThat(summary.timberMarks()).containsExactly("ZZ9998", "ZZ9997");
    assertThat(summary.primaryTimberMark()).isEqualTo("ZZ9997");
    json.putNull("primaryTimberMark");
    summary = mapper.treeToValue(json, GasAppraisal.AppraisedSummary.class);
    assertThat(summary.primaryTimberMark()).isNull();
    assertThat(summary.timberMarks()).hasSize(2);
    assertThat(mapper.readTree(mapper.writeValueAsBytes(summary))).isEqualTo(json);
  }

  @Test
  void ecasRowsRetainEachMarkAndPermitForTheSameSubmission() throws IOException {
    EcasInbox.Item[] items =
        mapper.treeToValue(fixture().get("ecasInboxMultiMarkItems"), EcasInbox.Item[].class);
    assertThat(items).extracting(EcasInbox.Item::ecasId)
        .containsOnly("999900000002");
    assertThat(items).extracting(EcasInbox.Item::timberMark)
        .containsExactly("ZZ9998", "ZZ9997", "ZZ9998");
    assertThat(items).extracting(EcasInbox.Item::cuttingPermit)
        .containsExactly("002", "002", "003");
    assertThat(items).allMatch(EcasInbox.Item::multipleTimberMarks);

    EcasReference.Coast reference = mapper.treeToValue(
        fixture().get("ecasCoastMultiMarkReference"), EcasReference.Coast.class);
    assertThat(reference.header().ecasId()).isEqualTo(items[0].ecasId());
    assertThat(reference.header().revisionCount()).isEqualTo(2);
    assertThat(reference.timberMarks()).extracting(EcasReference.TimberMark::timberMark)
        .containsExactly("ZZ9998", "ZZ9997");
    assertThat(reference.timberMarks()).extracting(EcasReference.TimberMark::revisionCount)
        .containsExactly(3, 4);
  }

  @Test
  void gasRowsKeepTheirMarksWhileLinkingToTheSameWorksheetAndCoastSubmission() throws IOException {
    GasAppraisal.SearchResult result =
        mapper.treeToValue(fixture().get("gasSearchResult"), GasAppraisal.SearchResult.class);
    GasAppraisal.AppraisedSummary summary = mapper.treeToValue(
        fixture().get("gasMultiMarkAppraisedSummary"), GasAppraisal.AppraisedSummary.class);
    EcasReference.Coast reference = mapper.treeToValue(
        fixture().get("ecasCoastMultiMarkReference"), EcasReference.Coast.class);
    assertThat(result.appraisals().items()).extracting(GasAppraisal.Item::key)
        .containsExactly(summary.key(), summary.key());
    assertThat(result.appraisals().items()).extracting(GasAppraisal.Item::timberMark)
        .containsExactlyElementsOf(summary.timberMarks());
    assertThat(summary.timberMarks()).containsExactly("ZZ9998", "ZZ9997");
    assertThat(summary.primaryTimberMark()).isEqualTo(reference.primaryTimberMark());
    assertThat(summary.ecasId()).isEqualTo(reference.header().ecasId());
    assertThat(summary.appraisalMethod()).isEqualTo(reference.header().appraisalMethod());
    assertThat(summary.rates().getFirst().totalStumpageRate()).isEqualTo(new BigDecimal("12.30"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "12.30", "9999.99", "-9999.99"})
  void storedRateAmountsAreExactWithinTheDeclaredPrecision(String amount) throws IOException {
    GasAppraisal.StoredRate rate = rate(amount);
    assertThat(rate.totalStumpageRate().scale()).isEqualTo(2);
    assertThat(mapper.valueToTree(rate).get("totalStumpageRate").asText())
        .isEqualTo(new BigDecimal(amount).setScale(2).toPlainString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"10000", "-10000", "0.001", "12.345"})
  void storedRateAmountsNeverSilentlyRoundOrOverflow(String amount) {
    assertThatIllegalArgumentException().isThrownBy(() -> rate(amount));
  }

  private GasAppraisal.StoredRate rate(String amount) {
    return new GasAppraisal.StoredRate("999900000020", LocalDate.of(2026, 10, 1), new BigDecimal(amount));
  }

  private EcasInbox.Search search(String json) throws IOException {
    return mapper.readValue(json, EcasInbox.Search.class);
  }

  private JsonNode fixture() throws IOException {
    try (var input = getClass().getResourceAsStream("/contracts/synthetic-workflow.json")) {
      return mapper.readTree(input);
    }
  }
}
