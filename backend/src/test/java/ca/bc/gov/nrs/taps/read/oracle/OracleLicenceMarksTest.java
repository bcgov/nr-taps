package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsCapability;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Types;
import java.util.Arrays;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleLicenceMarksTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleLicenceMarks repository = new OracleLicenceMarks(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @Test
  void scopesEachOriginalHaulingAuthorityMarkBeforeReturningTheChooser() throws SQLException {
    var user = idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ", "TAPS_REGION_CLERK_REGION-CARIBOO");

    var choices = repository.forLicence(user, " a00001 ");

    assertThat(choices.licence()).isEqualTo("A00001");
    assertThat(choices.timberMarks()).isEmpty();
    String sql = preparedSql();
    String chooserSql = sql.substring(sql.lastIndexOf("SELECT HA.TIMBER_MARK"));
    assertThat(chooserSql).contains("FROM HAULING_AUTHORITY HA",
        "WHERE HA.FOREST_FILE_ID = I.FOREST_FILE_ID", "AND EXISTS (",
        "SELECT 1 FROM scoped_parents P", "P.FOREST_FILE_ID = HA.FOREST_FILE_ID",
        "AND P.TIMBER_MARK = HA.TIMBER_MARK", "ORDER BY HA.TIMBER_MARK")
        .doesNotContain("DISTINCT", "UNION", "JOIN scoped_parents");
    assertThat(sql).contains(ReadScopePredicate.forCapability(user, TapsCapability.GAS_APPRAISAL_VIEW).sql());
    assertThat(sql).doesNotContain("A00001", "DZZ", "RCB", "APPRAISED_WORKSHEET", "ADS_SUBMITTED_TIMBER_MARK");
    assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(4);
    verify(statement).setString(1, "A00001");
    verify(statement).setNull(2, Types.VARCHAR);
    verify(statement).setString(3, "DZZ");
    verify(statement).setString(4, "RCB");
    verify(connection, never()).createStatement();
    verify(statement).executeQuery();
    verifyCleanup(true);
  }

  @Test
  void preservesEveryReturnedMarkStringAndItsOrderIndependentlyOfWorksheets() throws SQLException {
    when(rows.next()).thenReturn(true, true, true, false);
    when(rows.getString("TIMBER_MARK")).thenReturn("000001", " ZZ002", "000001");

    var choices = repository.forLicence(idir("TAPS_ADMIN"), "0000123456");

    assertThat(choices.licence()).isEqualTo("0000123456");
    assertThat(choices.timberMarks()).containsExactly("000001", " ZZ002", "000001");
    assertThatThrownBy(() -> choices.timberMarks().add("ZZ0003"))
        .isInstanceOf(UnsupportedOperationException.class);
    verify(statement).setString(1, "0000123456");
    verifyCleanup(true);
  }

  @Test
  void noAuthorizedMarksReturnsAnEmptyList() throws SQLException {
    var choices = repository.forLicence(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), "a00001");

    assertThat(choices.licence()).isEqualTo("A00001");
    assertThat(choices.timberMarks()).isEmpty();
    verifyCleanup(true);
  }

  @Test
  void unrelatedProvincialAndDistrictRolesCannotLendTheirScope() throws SQLException {
    repository.forLicence(idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DYY",
        "TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), "a00001");

    assertThat(preparedSql()).contains("(record_scope.ADMIN_DISTRICT_CODE = ?)")
        .doesNotContain("(1 = 1)", "DYY");
    verify(statement).setString(3, "DZZ");
  }

  @Test
  void noGasCapabilityRemainsDeniedEvenWithAnExactLicence() throws SQLException {
    repository.forLicence(idir("TAPS_HEADQUARTERS", "TAPS_VIEWER_DISTRICT-DZZ"), "a00001");

    String sql = preparedSql();
    assertThat(sql).contains("(1 = 0)").doesNotContain("(1 = 1)");
    assertThat(sql.chars().filter(character -> character == '?').count()).isEqualTo(2);
  }

  @Test
  void permissionAndWildcardTextStayLiteralBoundValues() throws SQLException {
    repository.forLicence(idir("TAPS_DISTRICT_APPRAISER_DISTRICT-DZZ"), "a'_%--");

    assertThat(preparedSql()).doesNotContain("A'_%--", " LIKE ");
    verify(statement).setString(1, "A'_%--");
    verify(statement).setNull(2, Types.VARCHAR);
    verify(statement).setString(3, "DZZ");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "12345678901", "123456789\u00df"})
  void rejectsMissingOverlengthAndUppercaseExpandedLicencesBeforeReading(String licence) {
    assertThatThrownBy(() -> repository.forLicence(idir("TAPS_ADMIN"), licence))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(dataSource);
  }

  @Test
  void queryFailurePropagatesAndReleasesJdbcResources() throws SQLException {
    SQLException failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);

    assertThatThrownBy(() -> repository.forLicence(idir("TAPS_ADMIN"), "a00001"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(false);
  }

  @Test
  void bindingFailurePropagatesWithoutExecutingAQuery() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic binding failure", "22000");
    doThrow(failure).when(statement).setNull(2, Types.VARCHAR);

    assertThatThrownBy(() -> repository.forLicence(idir("TAPS_ADMIN"), "a00001"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verify(statement, never()).executeQuery();
    verifyCleanup(false);
  }

  @Test
  void mappingFailurePropagatesWithoutReturningPartialChoices() throws SQLException {
    SQLException failure = new SQLDataException("Synthetic mapping failure", "22000");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getString("TIMBER_MARK")).thenReturn("ZZ0001").thenThrow(failure);

    assertThatThrownBy(() -> repository.forLicence(idir("TAPS_ADMIN"), "a00001"))
        .isInstanceOf(DataAccessException.class).hasCause(failure);
    verifyCleanup(true);
  }

  private String preparedSql() throws SQLException {
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    return sql.getValue();
  }

  private void verifyCleanup(boolean resultSetWasOpened) throws SQLException {
    if (resultSetWasOpened) {
      verify(rows).close();
    }
    verify(statement).close();
    verify(connection).close();
  }

  private TapsUser idir(String... roles) {
    return new TapsUser("synthetic", "Synthetic user", null, IdentityProvider.IDIR, null,
        Arrays.stream(roles)
            .map(role -> RoleGrant.accept(FamRoleName.parse(role), IdentityProvider.IDIR).orElseThrow())
            .toList());
  }
}
