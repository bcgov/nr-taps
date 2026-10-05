package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Stored metadata for individual (non-ZIP) documents; no file content. */
public final class EcasAttachments {
  public static final int PAGE_SIZE = 50;
  public static final int MAX_PAGE = 1_000_000;
  private EcasAttachments() {}

  public record Item(String documentId, CodeOption documentType, String transmissionTypeCode,
      String fileName, String description, Integer revisionCount,
      LocalDateTime createdAt, LocalDateTime updatedAt) {
    public Item {
      documentId = LegacyIdentifiers.requiredId(documentId);
      Objects.requireNonNull(documentType, "documentType");
      fileName = displayFileName(fileName);
    }
  }

  public record Page(String ecasId, AppraisalMethod appraisalMethod, List<Item> items, long total,
      int page, int size) {
    public Page {
      ecasId = LegacyIdentifiers.requiredId(ecasId);
      Objects.requireNonNull(appraisalMethod, "appraisalMethod");
      items = List.copyOf(items);
      validatePage(page);
      if (size != PAGE_SIZE || items.size() > size || total < items.size()) {
        throw new IllegalArgumentException("invalid attachment page");
      }
    }
  }

  public static void validatePage(int page) {
    if (page < 0 || page > MAX_PAGE) throw new IllegalArgumentException("invalid attachment page");
  }

  private static String displayFileName(String value) {
    if (value == null) return null;
    String name = value.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    if (name.matches("^[A-Za-z]:.*")) name = name.substring(2);
    return name.isEmpty() ? null : name;
  }
}
