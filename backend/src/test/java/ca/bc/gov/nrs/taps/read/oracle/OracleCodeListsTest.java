package ca.bc.gov.nrs.taps.read.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.bc.gov.nrs.taps.read.EffectiveCode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLDataException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OracleCodeListsTest {
  private final DataSource dataSource = mock(DataSource.class);
  private final Connection connection = mock(Connection.class);
  private final PreparedStatement statement = mock(PreparedStatement.class);
  private final ResultSet rows = mock(ResultSet.class);
  private final OracleCodeLists codeLists = new OracleCodeLists(new JdbcTemplate(dataSource));

  @BeforeEach
  void prepareJdbcBoundary() throws SQLException {
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(anyString())).thenReturn(statement);
    when(statement.executeQuery()).thenReturn(rows);
  }

  @ParameterizedTest
  @MethodSource("lookups")
  void preparesSourceEquivalentSqlAndKeepsEmptyResultsEmpty(
      String sourceColumn, Function<OracleCodeLists, List<EffectiveCode>> lookup) throws SQLException {
    assertThat(lookup.apply(codeLists)).isEmpty();

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(connection).prepareStatement(sql.capture());
    assertThat(sql.getValue()).isEqualToNormalizingWhitespace("""
        SELECT C.%s AS CODE,
               C.DESCRIPTION,
               C.EFFECTIVE_DATE,
               C.EXPIRY_DATE,
               C.UPDATE_TIMESTAMP
          FROM %s C
         WHERE SYSDATE BETWEEN C.EFFECTIVE_DATE AND C.EXPIRY_DATE
         ORDER BY CODE
        """.formatted(sourceColumn, sourceColumn));
    verify(connection, never()).createStatement();
    verify(statement).executeQuery();
    verifyCleanup(true);
  }

  @ParameterizedTest
  @MethodSource("lookups")
  void preservesEveryProjectedValueAndLeavesFilteringAndOrderToOracle(
      String sourceColumn, Function<OracleCodeLists, List<EffectiveCode>> lookup) throws SQLException {
    LocalDateTime effective = LocalDateTime.of(2035, 1, 2, 3, 4, 5);
    LocalDateTime expiry = LocalDateTime.of(2035, 1, 2, 23, 59, 58);
    LocalDateTime updated = LocalDateTime.of(2034, 12, 31, 18, 19, 20);
    when(rows.next()).thenReturn(true, true, true, false);
    when(rows.getString("CODE")).thenReturn(" Z ", "A", "A");
    when(rows.getString("DESCRIPTION")).thenReturn(" Mixed Case ", null, null);
    when(rows.getTimestamp("EFFECTIVE_DATE")).thenReturn(Timestamp.valueOf(effective), null, null);
    when(rows.getTimestamp("EXPIRY_DATE")).thenReturn(Timestamp.valueOf(expiry), null, null);
    when(rows.getTimestamp("UPDATE_TIMESTAMP")).thenReturn(Timestamp.valueOf(updated), null, null);

    // Mocked rows; Oracle's date filter is covered by OracleReadIT.
    assertThat(lookup.apply(codeLists)).containsExactly(
        new EffectiveCode(" Z ", " Mixed Case ", effective, expiry, updated),
        new EffectiveCode("A", null, null, null, null),
        new EffectiveCode("A", null, null, null, null));
    verifyCleanup(true);
  }

  @ParameterizedTest
  @MethodSource("lookups")
  void queryFailuresPropagateAndReleaseJdbcResources(
      String sourceColumn, Function<OracleCodeLists, List<EffectiveCode>> lookup) throws SQLException {
    SQLException failure = new SQLSyntaxErrorException("Synthetic query failure", "42000");
    when(statement.executeQuery()).thenThrow(failure);

    assertThatThrownBy(() -> lookup.apply(codeLists))
        .isInstanceOf(DataAccessException.class)
        .hasCause(failure);
    verifyCleanup(false);
  }

  @ParameterizedTest
  @MethodSource("lookups")
  void rowMappingFailuresPropagateWithoutReturningAPartialList(
      String sourceColumn, Function<OracleCodeLists, List<EffectiveCode>> lookup) throws SQLException {
    SQLException failure = new SQLDataException("Synthetic timestamp read failure", "22000");
    when(rows.next()).thenReturn(true, true, false);
    when(rows.getString("CODE")).thenReturn("A", "B");
    when(rows.getTimestamp("EFFECTIVE_DATE")).thenReturn(null).thenThrow(failure);

    assertThatThrownBy(() -> lookup.apply(codeLists))
        .isInstanceOf(DataAccessException.class)
        .hasCause(failure);
    verifyCleanup(true);
  }

  private void verifyCleanup(boolean resultSetWasOpened) throws SQLException {
    if (resultSetWasOpened) {
      verify(rows).close();
    }
    verify(statement).close();
    verify(connection).close();
  }

  static Stream<Arguments> lookups() {
    return Stream.of(
        Arguments.of("APPRAISAL_METHOD_CODE",
            (Function<OracleCodeLists, List<EffectiveCode>>) OracleCodeLists::appraisalMethods),
        Arguments.of("APPRAISAL_STATUS_CODE",
            (Function<OracleCodeLists, List<EffectiveCode>>) OracleCodeLists::appraisalStatuses),
        Arguments.of("RATE_ADJUSTMENT_TYPE_CODE",
            (Function<OracleCodeLists, List<EffectiveCode>>) OracleCodeLists::rateAdjustmentTypes));
  }
}
