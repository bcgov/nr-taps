package ca.bc.gov.nrs.taps.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.read.EcasAudit;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAudit;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.io.StringReader;
import java.util.Arrays;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Audit checks run by OracleReadIT inside its rolled-back fixture transaction. */
final class EcasAuditOracleAssertions {
  private EcasAuditOracleAssertions() {}

  static void verify(JdbcTemplate jdbc) {
    var reader = new OracleEcasAudit(jdbc);
    var cariboo = user(IdentityProvider.IDIR, "TAPS_REGION_APPRAISER_REGION-CARIBOO");
    var other = user(IdentityProvider.IDIR, "TAPS_REGION_APPRAISER_REGION-OMINECA");
    var client = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001");
    var first = reader.history(cariboo, "1001", 0).orElseThrow();
    assertThat(first.items()).extracting(EcasAudit.Event::eventId).contains("60001", "60002", "60003", "60005");
    assertThat(first.items().getFirst().eventId()).isEqualTo("60001");
    assertThat(first.items().getFirst().fileName()).isEqualTo("submission.xml");
    var wrongFile = first.items().stream().filter(row -> row.eventId().equals("60003")).findFirst().orElseThrow();
    assertThat(wrongFile.fileName()).isNull();
    assertThat(wrongFile.submittedFileId()).isNull();
    assertThat(reader.history(other, "1001", 0)).isEmpty();
    assertThat(reader.history(user(IdentityProvider.IDIR), "1001", 0)).isEmpty();
    assertThat(reader.history(client, "1001", 0)).isPresent();
    assertThat(reader.details(client, "1001", "60004", 0)).isEmpty();
    assertThat(reader.details(other, "1001", "60001", 0)).isEmpty();

    var detail = reader.details(cariboo, "1001", "60001", 0).orElseThrow();
    assertThat(detail.comment()).contains("<script>text only</script>");
    assertThat(detail.commentTruncated()).isFalse();
    assertThat(detail.items()).extracting(EcasAudit.FieldChange::detailId).containsExactly("61001", "61002");
    assertThat(detail.items().getLast().previousValue()).isEqualTo("0");
    assertThat(detail.items().getLast().changedValue()).isNull();
    var deleted = reader.details(cariboo, "1001", "60003", 0).orElseThrow().items().getFirst();
    assertThat(deleted.columnName()).isEqualTo("FILE_NAME");
    assertThat(deleted.previousValue()).isEqualTo("deleted.xml");
    assertThat(deleted.businessIdentifier()).isEqualTo("deleted.xml");
    var imported = reader.details(client, "1001", "60002", 0).orElseThrow();
    assertThat(imported.event().commentsSuppressed()).isTrue();
    assertThat(imported.event().commentPreview()).isNull();
    assertThat(imported.event().hasMoreComment()).isFalse();
    assertThat(imported.comment()).isNull();

    String text = "A" + "😀".repeat(3000);
    jdbc.update("INSERT INTO ECAS_AUDIT_COMMENT VALUES (1001, 60005, ?)", statement ->
        statement.setCharacterStream(1, new StringReader(text), text.length()));
    var longComment = reader.details(cariboo, "1001", "60005", 0).orElseThrow();
    assertThat(longComment.comment()).isEqualTo("A" + "😀".repeat(1999));
    assertThat(longComment.commentTruncated()).isTrue();

    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'DFT' WHERE ECAS_ID = 1001");
    var viewer = user(IdentityProvider.IDIR, "TAPS_VIEWER_DISTRICT-DCA");
    assertThat(reader.history(viewer, "1001", 0)).isEmpty();
    assertThat(reader.details(viewer, "1001", "60001", 0)).isEmpty();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'SCN' WHERE ECAS_ID = 1001");
    assertThat(reader.history(client, "1001", 0)).isEmpty();
    jdbc.update("UPDATE APPRAISAL_DATA_SUBMISSION SET APPRAISAL_STATUS_CODE = 'CON' WHERE ECAS_ID = 1001");

    for (int index = 0; index < 105; index++) {
      jdbc.update("INSERT INTO ECAS_AUDIT_EVENT (AUDIT_EVENT_ID, ECAS_ID, ECAS_ACTION_CODE, ENTRY_TIMESTAMP, ENTRY_USERID) VALUES (?, 1001, 'UPD', DATE '2026-02-01', 'IDIR\\SYNTHETIC')", 60200 + index);
      jdbc.update("INSERT INTO ECAS_AUDIT_DETAIL (AUDIT_DETAIL_ID,AUDIT_EVENT_ID,ECAS_ID,ENTRY_USERID,ENTRY_TIMESTAMP,COLUMN_NAME) VALUES (?,60001,1001,'IDIR\\SYNTHETIC',DATE '2026-02-01','FIELD')", 61200 + index);
    }
    first = reader.history(cariboo, "1001", 0).orElseThrow();
    var second = reader.history(cariboo, "1001", 1).orElseThrow();
    assertThat(first.items()).hasSize(100).doesNotContainAnyElementsOf(second.items());
    assertThat(second.total()).isEqualTo(first.total());
    assertThat(second.items()).hasSize((int) first.total() - 100);
    assertThat(reader.history(cariboo, "1001", 9).orElseThrow().total()).isEqualTo(first.total());
    assertThat(reader.history(cariboo, "1001", 9).orElseThrow().items()).isEmpty();
    var firstChanges = reader.details(cariboo, "1001", "60001", 0).orElseThrow();
    var secondChanges = reader.details(cariboo, "1001", "60001", 1).orElseThrow();
    assertThat(firstChanges.total()).isEqualTo(107);
    assertThat(firstChanges.items()).hasSize(100).doesNotContainAnyElementsOf(secondChanges.items());
    assertThat(secondChanges.items()).hasSize(7);
    assertThat(reader.details(cariboo, "1001", "60001", 9).orElseThrow().total()).isEqualTo(107);

    jdbc.update("INSERT INTO ECAS_AUDIT_COMMENT VALUES (1001,60001,'ambiguous duplicate')");
    assertThatThrownBy(() -> reader.details(cariboo, "1001", "60001", 0)).isInstanceOf(DataAccessException.class);
  }

  private static TapsUser user(IdentityProvider provider, String... roles) {
    return new TapsUser("synthetic", "Synthetic", null, provider, null, Arrays.stream(roles)
        .map(FamRoleName::parse).map(role -> RoleGrant.accept(role, provider).orElseThrow()).toList());
  }
}
