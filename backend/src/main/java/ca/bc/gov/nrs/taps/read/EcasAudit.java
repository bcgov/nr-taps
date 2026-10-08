package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only audit history and event details (ECAS03/04). */
public final class EcasAudit {
  public static final int PAGE_SIZE = 100;
  public static final int COMMENT_LIMIT = 4000;
  public static final int VALUE_LIMIT = 4000;

  private EcasAudit() {}

  public record Event(String eventId, String userId,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime eventDate,
      CodeOption action, String sentToUserId, String submittedFileId, String fileName,
      String commentPreview, boolean hasMoreComment, boolean commentsSuppressed) {
    public Event {
      eventId = LegacyIdentifiers.requiredId(eventId);
      submittedFileId = LegacyIdentifiers.optionalId(submittedFileId);
    }
  }

  public record HistoryPage(String ecasId, List<Event> items, long total, int page) {
    public HistoryPage {
      ecasId = LegacyIdentifiers.requiredId(ecasId);
      items = List.copyOf(items);
      checkPage(items.size(), total, page);
    }
  }

  public record FieldChange(String detailId, String userId,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime eventDate,
      String businessIdentifier, String tableName, String columnName,
      String previousValue, String changedValue,
      boolean previousValueTruncated, boolean changedValueTruncated) {
    public FieldChange {
      detailId = LegacyIdentifiers.requiredId(detailId);
    }
  }

  public record DetailPage(String ecasId, Event event, String comment, boolean commentTruncated,
      List<FieldChange> items, long total, int page) {
    public DetailPage {
      ecasId = LegacyIdentifiers.requiredId(ecasId);
      items = List.copyOf(items);
      checkPage(items.size(), total, page);
    }
  }

  private static void checkPage(int size, long total, int page) {
    if (page < 0 || total < size || size > PAGE_SIZE) {
      throw new IllegalArgumentException("invalid audit page");
    }
  }
}
