package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasReference;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleEcasReferenceTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleEcasReference references = new OracleEcasReference(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @Test
  void appliesOwnershipAndMethodBeforeReadingDisplayDefaultsOrMarks() throws SQLException {
    assertThat(references.coast(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), "00012345"))
        .isEmpty();
    String sql = preparedSql();
    assertThat(sql).contains("ADS.*", "ADSC.APPRAISAL_METHOD_CODE",
        "DISTRICT.ORG_UNIT_NO = ADS.ADMIN_DISTRICT", "DISTRICT.ROLLUP_REGION_NO",
        "record_scope.ECAS_ID = ? AND record_scope.APPRAISAL_METHOD_CODE = ?",
        "record_scope.ADMIN_DISTRICT_CODE = ?", "FROM scoped_parent P", "FROM enriched_parent P",
        "JOIN ADS_SUBMITTED_TIMBER_MARK M ON M.ECAS_ID = P.ECAS_ID",
        "ORDER BY ROW_KIND, TIMBER_MARK");
    assertThat(sql).doesNotContain("ADSC.CLIENT_NUMBER", "PKG_", "UPDATE ", "INSERT ", "DELETE ",
        "12345", "DZZ", "APPRAISED_WORKSHEET");
    assertThat(sql.split("UNION ALL", -1)).hasSize(2);
    verify(statement).setLong(1, 12345L);
    verify(statement).setString(2, "C");
    verify(statement).setString(3, "DZZ");
    verify(statement).executeQuery();
    verify(connection, never()).createStatement();
    cleanup();
  }

  @Test
  void directReferenceRetainsViewerDraftRestriction() throws SQLException {
    assertThat(references.interior(idir("TAPS_VIEWER_DISTRICT-DZZ"), "12345")).isEmpty();
    assertThat(preparedSql()).contains("record_scope.STATUS_CODE <> ?");
    verify(statement).setString(2, "I");
    verify(statement).setString(3, "DZZ");
    verify(statement).setString(4, "DFT");
  }

  @Test
  void absentCapabilityProducesDenyPredicate() throws SQLException {
    assertThat(references.coast(idir(), "12345")).isEmpty();
    assertThat(preparedSql()).contains("AND (1 = 0)");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "1 OR 1=1", "-1", "999999999999999999999999"})
  void rejectsInvalidIdBeforeJdbc(String id) {
    assertThatThrownBy(() -> references.coast(idir("TAPS_ADMIN"), id))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void translatesLegacyDefaultsWithoutStoredProcedureExecution() throws SQLException {
    references.coast(idir("TAPS_ADMIN"), "12345");
    assertThat(preparedSql()).contains(
        "P.FTAS_DATA_VALIDATED_IND = 'Y'",
        "X.PRIMARY_MARK_IND = 'Y'",
        "CP.MGMT_UNIT_ID IS NOT NULL AND CP.MGMT_UNIT_TYPE_CODE IS NOT NULL",
        "THEN CP.MGMT_UNIT_ID ELSE PFU.MGMT_UNIT_ID",
        "JOIN ORG_UNIT GEO ON GEO.ORG_UNIT_NO = HVA.GEOGRAPHIC_DISTRICT",
        "C.LEGAL_FIRST_NAME || C.CLIENT_NAME", "FROM V_CLIENT_PUBLIC C",
        "COALESCE(P.FTA_TSA_CODE, SUBSTR(P.TSB_CODE, 1, 2))",
        "SELECT NVL(SUM(V.SPECIES_VOLUME), 0) FROM ADS_SPECIES_VOLUME V",
        "SELECT DISTINCT D.ADS_LOCATION_CODE FROM ADS_CUTTING_AUTHORITY_DETAIL D",
        "P.ADS_LOCATION_DISTANCE_AVERAGE");
  }

  @Test
  void readsStoredStandRateEligibilityWithoutInferringNewFormDefaults() throws SQLException {
    references.coast(idir("TAPS_ADMIN"), "12345");
    String sql = preparedSql();
    assertThat(sql).contains(
        "P.CONIF_STAND_RATE_ELIG_CODE, P.DECID_STAND_RATE_ELIG_CODE",
        "SELECT S.DESCRIPTION FROM STAND_RATE_ELIGIBILITY_CODE S",
        "S.STAND_RATE_ELIGIBILITY_CODE = P.CONIF_STAND_RATE_ELIG_CODE",
        "S.STAND_RATE_ELIGIBILITY_CODE = P.DECID_STAND_RATE_ELIG_CODE");
    assertThat(sql)
        .doesNotContain("NVL(P.CONIF_STAND_RATE_ELIG_CODE", "NVL(P.DECID_STAND_RATE_ELIG_CODE",
            "APP_METHOD_STAND_RATE_XREF", "SYSDATE");
  }

  @Test
  void preservesInteriorDateBoundaryAndLatestApprovedZoneFallback() throws SQLException {
    references.interior(idir("TAPS_ADMIN"), "12345");
    String sql = preparedSql();
    assertThat(sql).contains(
        "P.APPRAISAL_EFFECTIVE_DATE < DATE '2018-11-01'",
        "P.APPRAISAL_SELL_PRICE_ZONE_CODE IS NULL",
        "P.POINT_OF_APPRAISAL_CODE IS NOT NULL",
        "SELECT MAX(A.EFFECTIVE_DATE) FROM POINT_OF_APPRAISAL A",
        "AND A.APPROVED_IND = 'Y'",
        "ELSE P.APPRAISAL_SELL_PRICE_ZONE_CODE END",
        "NVL(P.COMPARATIVE_CRUISE_IND, 'N')");
    assertThat(sql).doesNotContain("SYSDATE", "ROWNUM", "FETCH FIRST");
  }

  @Test
  void preservesCoastMarkRowsRevisionsAndDecimalVolumes() throws SQLException {
    parent("C");
    when(rows.next()).thenReturn(true, true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1, 1);
    when(rows.getString("TIMBER_MARK")).thenReturn("A12345", "B12345");
    when(rows.getString("PRIMARY_MARK_IND")).thenReturn("Y", "N");
    when(rows.getObject("MARK_REVISION_COUNT", Integer.class)).thenReturn(7, 3);
    when(rows.getBigDecimal("MARK_CRUISE_VOLUME"))
        .thenReturn(new BigDecimal("1234.567"), new BigDecimal("0.000"));
    when(rows.getBigDecimal("NET_CRUISE_VOLUME")).thenReturn(new BigDecimal("1234.567"));
    when(rows.getBigDecimal("NET_MERCHANTABLE_AREA")).thenReturn(new BigDecimal("20.50"));
    when(rows.getBigDecimal("INITIAL_MERCHANTABLE_AREA")).thenReturn(new BigDecimal("21.60"));
    when(rows.getBigDecimal("POINT_OF_APPRAISAL_DISTANCE")).thenReturn(new BigDecimal("41.11"));
    when(rows.getString("MAJOR_CENTRE_CODE")).thenReturn("01");
    when(rows.getBigDecimal("ADS_LOCATION_DISTANCE_AVERAGE")).thenReturn(new BigDecimal("28.90"));

    var reference = references.coast(idir("TAPS_ADMIN"), "12345").orElseThrow();
    assertThat(reference.primaryTimberMark()).isEqualTo("A12345");
    assertThat(reference.timberMarks()).containsExactly(
        new EcasReference.TimberMark("A12345", new BigDecimal("1234.567"), true, 7),
        new EcasReference.TimberMark("B12345", new BigDecimal("0.000"), false, 3));
    assertThat(reference.referenceMark()).isEqualTo("R12345");
    assertThat(reference.netCruiseVolume()).isEqualTo(new BigDecimal("1234.567"));
    assertThat(reference.netMerchantableArea()).isEqualTo(new BigDecimal("20.50"));
    assertThat(reference.initialMerchantableArea()).isEqualTo(new BigDecimal("21.60"));
    assertThat(reference.pointOfAppraisalDistance()).isEqualTo(new BigDecimal("41.11"));
    assertThat(reference.majorCentreCode()).isEqualTo("01");
    assertThat(reference.majorCentreDistance()).isEqualTo(new BigDecimal("28.90"));
    assertHeader(reference.header(), AppraisalMethod.C);
    cleanup();
  }

  @Test
  void preservesInteriorFieldsAndSeparateMarkRevision() throws SQLException {
    parent("I");
    oneMark();
    when(rows.getObject("MARK_REVISION_COUNT", Integer.class)).thenReturn(7);
    when(rows.getString("POINT_OF_APPRAISAL_CODE")).thenReturn("17");
    when(rows.getString("POINT_OF_APPRAISAL_DESCRIPTION")).thenReturn("Synthetic point");
    when(rows.getString("SELLING_PRICE_ZONE_CODE")).thenReturn("6");
    when(rows.getString("COMPARATIVE_CRUISE_IND")).thenReturn("N");
    when(rows.getString("SALVAGE_IND")).thenReturn("Y");

    var reference = references.interior(idir("TAPS_ADMIN"), "12345").orElseThrow();
    assertThat(reference.timberMark()).isEqualTo("A12345");
    assertThat(reference.timberMarkRevisionCount()).isEqualTo(7);
    assertThat(reference.pointOfAppraisal()).isEqualTo(new CodeOption("17", "Synthetic point"));
    assertThat(reference.sellingPriceZoneCode()).isEqualTo("6");
    assertThat(reference.comparativeCruise()).isFalse();
    assertThat(reference.salvage()).isTrue();
    assertHeader(reference.header(), AppraisalMethod.I);
  }

  @Test
  void retainsNullOptionalFieldsAndMissingLookupLabels() throws SQLException {
    parent("I");
    oneMark();
    when(rows.getString("CLIENT_NUMBER")).thenReturn(null);
    when(rows.getString("LICENSEE_NAME")).thenReturn(null);
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn(null);
    when(rows.getTimestamp("APPRAISAL_EFFECTIVE_DATE")).thenReturn(null);
    when(rows.getObject("REVISION_COUNT", Integer.class)).thenReturn(null);
    when(rows.getString("SALVAGE_IND")).thenReturn(null);
    when(rows.getString("CONIF_STAND_RATE_ELIG_CODE")).thenReturn(null);
    when(rows.getString("CONIF_STAND_RATE_ELIG_DESCRIPTION")).thenReturn(null);
    when(rows.getString("DECID_STAND_RATE_ELIG_CODE")).thenReturn("X");
    when(rows.getString("DECID_STAND_RATE_ELIG_DESCRIPTION")).thenReturn(null);
    var reference = references.interior(idir("TAPS_ADMIN"), "12345").orElseThrow();
    assertThat(reference.header().revisionCount()).isNull();
    assertThat(reference.header().clientNumber()).isNull();
    assertThat(reference.header().licenseeName()).isNull();
    assertThat(reference.header().effectiveDate()).isNull();
    assertThat(reference.header().status()).isEqualTo(new CodeOption("CON", null));
    assertThat(reference.header().coniferousStandRateEligibility())
        .isEqualTo(new CodeOption(null, null));
    assertThat(reference.header().deciduousStandRateEligibility())
        .isEqualTo(new CodeOption("X", null));
    assertThat(reference.timberMarkRevisionCount()).isNull();
    assertThat(reference.pointOfAppraisal()).isEqualTo(new CodeOption(null, null));
    assertThat(reference.sellingPriceZoneCode()).isNull();
    assertThat(reference.salvage()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"C", "I"})
  void preservesNonDefaultStandRateEligibilityInBothReferenceVariants(String method)
      throws SQLException {
    parent(method);
    oneMark();
    when(rows.getString("CONIF_STAND_RATE_ELIG_CODE")).thenReturn("C");
    when(rows.getString("CONIF_STAND_RATE_ELIG_DESCRIPTION")).thenReturn("Synthetic cruise grade");
    when(rows.getString("DECID_STAND_RATE_ELIG_CODE")).thenReturn("A");
    when(rows.getString("DECID_STAND_RATE_ELIG_DESCRIPTION")).thenReturn("Synthetic all grades");
    EcasReference.Header header = method.equals("C")
        ? references.coast(idir("TAPS_ADMIN"), "12345").orElseThrow().header()
        : references.interior(idir("TAPS_ADMIN"), "12345").orElseThrow().header();
    assertThat(header.coniferousStandRateEligibility())
        .isEqualTo(new CodeOption("C", "Synthetic cruise grade"));
    assertThat(header.deciduousStandRateEligibility())
        .isEqualTo(new CodeOption("A", "Synthetic all grades"));
  }

  @Test
  void refusesAmbiguousEnrichmentWithoutPartialReference() throws SQLException {
    parent("C");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 0);
    assertThatThrownBy(() -> references.coast(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    cleanup();
  }

  @Test
  void refusesMultipleInteriorMarksInsteadOfDroppingRows() throws SQLException {
    parent("I");
    when(rows.next()).thenReturn(true, true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1, 1);
    when(rows.getString("TIMBER_MARK")).thenReturn("A12345", "B12345");
    assertThatThrownBy(() -> references.interior(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(IncorrectResultSizeDataAccessException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"N", "X"})
  void refusesMissingPrimaryOrInvalidPrimaryFlag(String primary) throws SQLException {
    parent("C");
    oneMark();
    when(rows.getString("PRIMARY_MARK_IND")).thenReturn(primary);
    assertThatThrownBy(() -> references.coast(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refusesReferenceWithWrongMethod() throws SQLException {
    parent("I");
    oneMark();
    assertThatThrownBy(() -> references.coast(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void refusesOrphanMarks() throws SQLException {
    when(rows.next()).thenReturn(true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(1);
    when(rows.getString("TIMBER_MARK")).thenReturn("A12345");
    assertThatThrownBy(() -> references.coast(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void queryFailurePropagatesAndClosesResources() throws SQLException {
    SQLException failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);
    assertThatThrownBy(() -> references.interior(idir("TAPS_ADMIN"), "12345"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement).close();
    verify(connection).close();
  }

  private void parent(String method) throws SQLException {
    when(rows.getString("ECAS_ID")).thenReturn("12345");
    when(rows.getString("APPRAISAL_METHOD_CODE")).thenReturn(method);
    when(rows.getObject("REVISION_COUNT", Integer.class)).thenReturn(2);
    when(rows.getString("LICENCE")).thenReturn("A12345");
    when(rows.getString("CUTTING_PERMIT")).thenReturn("001");
    when(rows.getString("CLIENT_NUMBER")).thenReturn("00001234");
    when(rows.getString("CLIENT_LOCN_CODE")).thenReturn("00");
    when(rows.getString("LICENSEE_NAME")).thenReturn("Synthetic Client");
    when(rows.getString("STATUS_CODE")).thenReturn("CON");
    when(rows.getString("STATUS_DESCRIPTION")).thenReturn("Confirmed");
    when(rows.getString("APPRAISAL_CATEGORY_CODE")).thenReturn("A");
    when(rows.getString("REAPPRAISAL_REASON_CODE")).thenReturn("R");
    when(rows.getString("RATE_CALC_METHOD_CODE")).thenReturn("MPS");
    when(rows.getTimestamp("APPRAISAL_EFFECTIVE_DATE"))
        .thenReturn(Timestamp.valueOf("2018-10-31 23:59:59"));
    when(rows.getTimestamp("APPRAISAL_EXPIRY_DATE"))
        .thenReturn(Timestamp.valueOf("2019-10-31 00:00:00"));
    when(rows.getString("DISPLAY_ADMIN_CODE")).thenReturn("DZZ");
    when(rows.getString("DISPLAY_ADMIN_NAME")).thenReturn("Synthetic admin");
    when(rows.getString("GEO_DISTRICT_CODE")).thenReturn("DXX");
    when(rows.getString("GEO_DISTRICT_NAME")).thenReturn("Synthetic geo");
    when(rows.getString("FILE_TYPE_CODE")).thenReturn("A01");
    when(rows.getString("FILE_TYPE_DESCRIPTION")).thenReturn("Synthetic licence");
    when(rows.getString("TSA_CODE")).thenReturn("12");
    when(rows.getString("TSA_DESCRIPTION")).thenReturn("Synthetic TSA");
    when(rows.getString("TSB_CODE")).thenReturn("123");
    when(rows.getString("TSB_DESCRIPTION")).thenReturn("Synthetic TSB");
    when(rows.getString("CONIF_STAND_RATE_ELIG_CODE")).thenReturn("S");
    when(rows.getString("CONIF_STAND_RATE_ELIG_DESCRIPTION")).thenReturn("Sawlog Grades");
    when(rows.getString("DECID_STAND_RATE_ELIG_CODE")).thenReturn("N");
    when(rows.getString("DECID_STAND_RATE_ELIG_DESCRIPTION")).thenReturn("No Grades");
    when(rows.getString("REFERENCE_TIMBER_MARK")).thenReturn("A12345");
    when(rows.getString("REFERENCE_MARK")).thenReturn("R12345");
  }

  private void oneMark() throws SQLException {
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getInt("ROW_KIND")).thenReturn(0, 1);
    when(rows.getString("TIMBER_MARK")).thenReturn("A12345");
    when(rows.getString("PRIMARY_MARK_IND")).thenReturn("Y");
  }

  private void assertHeader(EcasReference.Header header, AppraisalMethod method) {
    assertThat(header).isEqualTo(new EcasReference.Header(
        "12345", method, 2, "A12345", "001", "00001234", "00", "Synthetic Client",
        new CodeOption("CON", "Confirmed"), "A", "R", "MPS",
        LocalDate.of(2018, 10, 31), LocalDate.of(2019, 10, 31),
        new CodeOption("DZZ", "Synthetic admin"), new CodeOption("DXX", "Synthetic geo"),
        new CodeOption("A01", "Synthetic licence"), new CodeOption("12", "Synthetic TSA"),
        new CodeOption("123", "Synthetic TSB"), new CodeOption("S", "Sawlog Grades"),
        new CodeOption("N", "No Grades")));
  }

  private String preparedSql() throws SQLException {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    return sql.getValue();
  }

  private void cleanup() throws SQLException {
    verify(rows).close();
    verify(statement).close();
    verify(connection).close();
  }

  private TapsUser idir(String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles).map(role ->
            RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow()).toList());
  }
}
