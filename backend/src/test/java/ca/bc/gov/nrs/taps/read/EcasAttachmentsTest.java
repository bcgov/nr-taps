package ca.bc.gov.nrs.taps.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EcasAttachmentsTest {
  @Test
  void stripsClientPathsWithoutLosingNullableSourceMetadata() {
    var timestamp = LocalDateTime.of(2026, 1, 2, 13, 45, 56);
    var item = new EcasAttachments.Item("0001", new CodeOption("DOC", null), null,
        "C:\\private\\folder\\sample.pdf", "  original description  ", null, timestamp, null);
    assertThat(item.documentId()).isEqualTo("1");
    assertThat(item.fileName()).isEqualTo("sample.pdf");
    assertThat(item.description()).isEqualTo("  original description  ");
    assertThat(item.createdAt()).isEqualTo(timestamp);
    assertThat(item.updatedAt()).isNull();
    assertThat(new EcasAttachments.Item("2", new CodeOption("DOC", null), "P", null, null, null, null, null).fileName()).isNull();
    assertThat(new EcasAttachments.Item("3", new CodeOption("DOC", null), "E", "/internal/sample.pdf", null, null, null, null).fileName()).isEqualTo("sample.pdf");
    assertThat(new EcasAttachments.Item("4", new CodeOption("DOC", null), "E", "C:sample.pdf", null, null, null, null).fileName()).isEqualTo("sample.pdf");
  }

  @Test
  void pageBoundsAreFixedAndListsAreImmutable() {
    var page = new EcasAttachments.Page("1001", AppraisalMethod.C, List.of(), 0, 0, 50);
    assertThatThrownBy(() -> page.items().add(null)).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> EcasAttachments.validatePage(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EcasAttachments.validatePage(1_000_001)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EcasAttachments.Page("1001", AppraisalMethod.C, List.of(), -1, 0, 50)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EcasAttachments.Page("1001", AppraisalMethod.C, List.of(), 0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
  }
}
