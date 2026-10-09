package ca.bc.gov.nrs.taps.read;

import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Changed fields from appraised and non-appraised worksheet and rate snapshots. */
public final class GasAudit {
  public static final int PAGE_SIZE = 10;

  private GasAudit() {}

  public record Item(String eventId, String rateId, String userId,
      @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime eventDate,
      String attribute, String value, String comment) {
    public Item {
      Objects.requireNonNull(eventId, "eventId");
      rateId = LegacyIdentifiers.optionalId(rateId);
      Objects.requireNonNull(eventDate, "eventDate");
      Objects.requireNonNull(attribute, "attribute");
    }
  }

  public record HistoryPage(GasAppraisal.Key key, List<Item> items, long total, int page, int size) {
    public HistoryPage {
      Objects.requireNonNull(key, "key");
      if (key.type() == GasAppraisal.WorksheetType.HISTORIC) {
        throw new IllegalArgumentException("history requires an APPRAISED or NON_APPRAISED key");
      }
      items = List.copyOf(items);
      if (page < 0 || size != PAGE_SIZE || total < items.size() || items.size() > PAGE_SIZE) {
        throw new IllegalArgumentException("invalid history page");
      }
    }
  }
}
