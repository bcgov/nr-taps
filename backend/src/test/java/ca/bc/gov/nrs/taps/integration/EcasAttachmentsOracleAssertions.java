package ca.bc.gov.nrs.taps.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ca.bc.gov.nrs.taps.read.EcasAttachments;
import ca.bc.gov.nrs.taps.read.oracle.OracleEcasAttachments;
import ca.bc.gov.nrs.taps.security.FamRoleName;
import ca.bc.gov.nrs.taps.security.IdentityProvider;
import ca.bc.gov.nrs.taps.security.RoleGrant;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;

/** Attachment checks run by OracleReadIT after its fixtures load. */
final class EcasAttachmentsOracleAssertions {
  private EcasAttachmentsOracleAssertions() {}

  static void verify(JdbcTemplate jdbc) {
    var reader = new OracleEcasAttachments(jdbc);
    var coast = reader.forSubmission(user(IdentityProvider.IDIR, "TAPS_REGION_APPRAISER_REGION-CARIBOO"), "1001", 0).orElseThrow();
    assertThat(coast.items()).extracting(EcasAttachments.Item::documentId).containsExactly("7006", "7001", "7002");
    assertThat(coast.total()).isEqualTo(3);
    assertThat(coast.items().getFirst().fileName()).isNull();
    assertThat(coast.items().get(1).fileName()).isEqualTo("sample.pdf");
    assertThat(coast.items().get(1).createdAt()).isEqualTo(LocalDateTime.of(2026,1,1,12,34,56));
    assertThat(coast.items().get(1).updatedAt()).isNull();
    assertThat(reader.forSubmission(user(IdentityProvider.IDIR, "TAPS_REGION_APPRAISER_REGION-OMINECA"), "1001", 0)).isEmpty();
    var mixed = user(IdentityProvider.IDIR, "TAPS_VIEWER_DISTRICT-DCA", "TAPS_REGION_APPRAISER_REGION-OMINECA");
    assertThat(reader.forSubmission(mixed, "1001", 0).orElseThrow().items()).isEmpty();
    var licensee = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_VIEWER_FOREST_CLIENT-00000001");
    assertThat(reader.forSubmission(licensee, "1001", 0).orElseThrow().items())
        .extracting(EcasAttachments.Item::documentId).containsExactly("7006", "7001");
    var rpf = user(IdentityProvider.BCEID_BUSINESS, "TAPS_LICENSEE_SUBMITTER_FOREST_CLIENT-00000001");
    assertThat(reader.forSubmission(rpf, "1001", 0).orElseThrow().items())
        .extracting(EcasAttachments.Item::documentId).containsExactly("7006", "7001", "7003");
    var interiorViewer = user(IdentityProvider.IDIR, "TAPS_VIEWER_DISTRICT-DOM");
    assertThat(reader.forSubmission(interiorViewer, "1002", 0).orElseThrow().items())
        .extracting(EcasAttachments.Item::documentId).containsExactly("8003", "8001");
    assertThat(reader.forSubmission(user(IdentityProvider.IDIR, "TAPS_ADMIN"), "1001", 0).orElseThrow().items())
        .extracting(EcasAttachments.Item::documentId).containsExactly("7006", "7001", "7002", "7003", "7008");
    var emptyPage = reader.forSubmission(licensee, "1001", 1).orElseThrow();
    assertThat(emptyPage.items()).isEmpty();
    assertThat(emptyPage.total()).isEqualTo(2);
    // Check count and order across a page boundary.
    for (int offset = 0; offset < 55; offset++) {
      jdbc.update("""
          INSERT INTO ADS_SUPPORT_DOCUMENT
            (ECAS_ID, DOCUMENT_ID, APPRAISAL_DOCUMENT_TYPE_CODE, TRANSMISSION_TYPE_CODE, ZIP_FILE_IND)
          VALUES (1001, ?, 'PUB', 'P', 'N')
          """, 90000 + offset);
    }
    var first = reader.forSubmission(licensee, "1001", 0).orElseThrow();
    var second = reader.forSubmission(licensee, "1001", 1).orElseThrow();
    assertThat(first.items()).hasSize(50);
    assertThat(second.items()).hasSize(7);
    assertThat(first.total()).isEqualTo(57);
    assertThat(second.total()).isEqualTo(57);
    assertThat(first.items()).extracting(EcasAttachments.Item::documentId)
        .doesNotContainAnyElementsOf(second.items().stream().map(EcasAttachments.Item::documentId).toList());
  }

  private static TapsUser user(IdentityProvider provider, String... grants) {
    return new TapsUser("synthetic-attachment-user", "Synthetic reader", null, provider, null,
        Arrays.stream(grants).map(FamRoleName::parse).map(role -> RoleGrant.accept(role, provider).orElseThrow()).toList());
  }
}
