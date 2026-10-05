package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasAttachments;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** ECAS43/70 attachment metadata, limited to visible submissions and Coast document rules. */
public final class OracleEcasAttachments {
  private final JdbcTemplate jdbc;

  public OracleEcasAttachments(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<EcasAttachments.Page> forSubmission(TapsUser user, String ecasId, int page) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    EcasAttachments.validatePage(page);
    EcasReadPredicate parentScope = EcasReadPredicate.forUser(user);
    if (parentScope.sql().equals("(1 = 0)")) return Optional.empty();
    DocumentScope documentScope = documentScope(user);
    String sql = """
        WITH record_scope AS (
          SELECT ADS.ECAS_ID, ADSC.APPRAISAL_METHOD_CODE, ADS.APPRAISAL_EFFECTIVE_DATE,
                 ADS.APPRAISAL_STATUS_CODE AS STATUS_CODE, ADS.CLIENT_NUMBER,
                 OU.ORG_UNIT_CODE AS ADMIN_DISTRICT_CODE, REGION.ORG_UNIT_CODE AS ROLLUP_REGION_CODE
            FROM APPRAISAL_DATA_SUBMISSION ADS
            JOIN APPRAISAL_DATA_SUBMISSION_CTRL ADSC ON ADSC.ECAS_ID = ADS.ECAS_ID
            LEFT JOIN ORG_UNIT OU ON OU.ORG_UNIT_NO = ADS.ADMIN_DISTRICT
            LEFT JOIN ORG_UNIT REGION ON REGION.ORG_UNIT_NO = OU.ROLLUP_REGION_NO
           WHERE ADS.ECAS_ID = ? AND ADSC.APPRAISAL_METHOD_CODE IN ('C', 'I')
        ), permitted_parent AS (
          SELECT * FROM record_scope WHERE %s
        ), visible_documents AS (
          SELECT ASD.DOCUMENT_ID, ASD.APPRAISAL_DOCUMENT_TYPE_CODE, ADTC.DESCRIPTION AS TYPE_DESCRIPTION,
                 ASD.TRANSMISSION_TYPE_CODE, ESF.FILE_NAME, ASD.DOCUMENT_DESCRIPTION,
                 ASD.REVISION_COUNT, ASD.ENTRY_TIMESTAMP, ASD.UPDATE_TIMESTAMP, AAX.DISPLAY_ORDER
            FROM permitted_parent record_scope
            JOIN ADS_SUPPORT_DOCUMENT ASD ON ASD.ECAS_ID = record_scope.ECAS_ID
            JOIN APPRAISAL_ATTACHMENT_XREF AAX
              ON AAX.APPRAISAL_METHOD_CODE = record_scope.APPRAISAL_METHOD_CODE
             AND AAX.APPRAISAL_DOCUMENT_TYPE_CODE = ASD.APPRAISAL_DOCUMENT_TYPE_CODE
            JOIN APPRAISAL_DOCUMENT_TYPE_CODE ADTC
              ON ADTC.APPRAISAL_DOCUMENT_TYPE_CODE = ASD.APPRAISAL_DOCUMENT_TYPE_CODE
            LEFT JOIN ECAS_SUBMITTED_FILE ESF ON ESF.ECAS_ID = ASD.ECAS_ID
             AND ESF.ECAS_SUBMITTED_FILE_ID = ASD.ECAS_SUBMITTED_FILE_ID
           WHERE ASD.ZIP_FILE_IND = 'N'
             AND ADTC.EFFECTIVE_DATE <= NVL(record_scope.APPRAISAL_EFFECTIVE_DATE, SYSDATE)
             AND (record_scope.APPRAISAL_METHOD_CODE = 'I'
               OR NVL(record_scope.APPRAISAL_EFFECTIVE_DATE, SYSDATE) < ADTC.EXPIRY_DATE)
             AND %s
        ), numbered_documents AS (
          SELECT V.*, ROW_NUMBER() OVER (ORDER BY V.DISPLAY_ORDER ASC, V.DOCUMENT_ID DESC) AS PAGE_ROW
            FROM visible_documents V
        ), totals AS (
          SELECT COUNT(*) AS TOTAL_COUNT FROM visible_documents
        )
        SELECT P.ECAS_ID, P.APPRAISAL_METHOD_CODE, T.TOTAL_COUNT,
               (SELECT COUNT(*) FROM permitted_parent) AS PARENT_COUNT,
               D.DOCUMENT_ID, D.APPRAISAL_DOCUMENT_TYPE_CODE, D.TYPE_DESCRIPTION,
               D.TRANSMISSION_TYPE_CODE, D.FILE_NAME, D.DOCUMENT_DESCRIPTION,
               D.REVISION_COUNT, D.ENTRY_TIMESTAMP, D.UPDATE_TIMESTAMP
          FROM permitted_parent P CROSS JOIN totals T
          LEFT JOIN numbered_documents D ON D.PAGE_ROW BETWEEN ? AND ?
         ORDER BY D.PAGE_ROW
        """.formatted(parentScope.sql(), documentScope.sql());
    return jdbc.query(sql, statement -> {
      int index = 1;
      statement.setLong(index++, Long.parseLong(id));
      for (String parameter : parentScope.parameters()) statement.setString(index++, parameter);
      for (String parameter : documentScope.parameters()) statement.setString(index++, parameter);
      statement.setLong(index++, (long) page * EcasAttachments.PAGE_SIZE + 1);
      statement.setLong(index, (long) (page + 1) * EcasAttachments.PAGE_SIZE);
    }, rows -> {
      if (!rows.next()) return Optional.empty();
      String returnedId = rows.getString("ECAS_ID");
      AppraisalMethod method = AppraisalMethod.valueOf(rows.getString("APPRAISAL_METHOD_CODE"));
      long total = rows.getLong("TOTAL_COUNT");
      var items = new ArrayList<EcasAttachments.Item>();
      var documentIds = new HashSet<String>();
      do {
        if (rows.getInt("PARENT_COUNT") != 1) {
          throw new IncorrectResultSizeDataAccessException(1, rows.getInt("PARENT_COUNT"));
        }
        if (!id.equals(returnedId) || !id.equals(rows.getString("ECAS_ID"))) {
          throw new DataIntegrityViolationException("attachment parent does not match");
        }
        String documentId = rows.getString("DOCUMENT_ID");
        if (documentId != null) {
          if (!documentIds.add(documentId)) throw new DataIntegrityViolationException("ambiguous attachment metadata");
          items.add(new EcasAttachments.Item(documentId,
              new CodeOption(rows.getString("APPRAISAL_DOCUMENT_TYPE_CODE"), rows.getString("TYPE_DESCRIPTION")),
              rows.getString("TRANSMISSION_TYPE_CODE"), rows.getString("FILE_NAME"),
              rows.getString("DOCUMENT_DESCRIPTION"), rows.getObject("REVISION_COUNT", Integer.class),
              localTimestamp(rows, "ENTRY_TIMESTAMP"), localTimestamp(rows, "UPDATE_TIMESTAMP")));
        }
      } while (rows.next());
      return Optional.of(new EcasAttachments.Page(id, method, items, total, page, EcasAttachments.PAGE_SIZE));
    });
  }

  private record DocumentScope(String sql, List<String> parameters) {}

  private static DocumentScope documentScope(TapsUser user) {
    var alternatives = new ArrayList<String>();
    var parameters = new ArrayList<String>();
    for (RoleGrant grant : user.grants()) {
      TapsUser singleGrant = new TapsUser(user.userId(), user.displayName(), user.email(),
          user.identityProvider(), user.businessName(), List.of(grant));
      EcasReadPredicate scope = EcasReadPredicate.forUser(singleGrant);
      if (scope.sql().equals("(1 = 0)")) continue;
      // ECAS70 allows these read roles for every document type. Coast adds per-type flags, and a
      // viewer grant can't use another role's flags.
      String coast = switch (grant.role()) {
        case TAPS_ADMIN -> "1 = 1";
        case TAPS_HEADQUARTERS -> "AAX.HEADQUARTERS_ACCESS_IND = 'Y'";
        case TAPS_DISTRICT_APPRAISER -> "AAX.DISTRICT_ACCESS_IND = 'Y'";
        case TAPS_REGION_APPRAISER, TAPS_REGION_CLERK -> "AAX.REGION_ACCESS_IND = 'Y'";
        case TAPS_BCTS, TAPS_BCTS_SUBMITTER -> "AAX.BCTS_ACCESS_IND = 'Y'";
        case TAPS_LICENSEE, TAPS_LICENSEE_VIEWER -> "AAX.LICENSEE_ACCESS_IND = 'Y'";
        case TAPS_LICENSEE_SUBMITTER -> "AAX.RPF_ACCESS_IND = 'Y'";
        case TAPS_VIEWER -> "1 = 0";
      };
      alternatives.add("(" + scope.sql() + " AND (record_scope.APPRAISAL_METHOD_CODE = 'I'"
          + " OR (record_scope.APPRAISAL_METHOD_CODE = 'C' AND " + coast + ")))" );
      parameters.addAll(scope.parameters());
    }
    return new DocumentScope(alternatives.isEmpty() ? "(1 = 0)" : "(" + String.join(" OR ", alternatives) + ")",
        List.copyOf(parameters));
  }

  private static LocalDateTime localTimestamp(ResultSet rows, String column) throws SQLException {
    var value = rows.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }
}
