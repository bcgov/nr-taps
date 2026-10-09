package ca.bc.gov.nrs.taps.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class GasAuditTest {
  private final GasAppraisal.Key key = new GasAppraisal.Key(GasAppraisal.WorksheetType.NON_APPRAISED, "123");
  private final GasAudit.Item item = new GasAudit.Item("W:301:3", null, null,
      LocalDateTime.of(2030, 1, 1, 12, 34, 56), "Expiry Date", null, null);

  @Test
  void pageKeepsNullsExactTimesCompositeIdentityAndItsFixedFamily() throws Exception {
    var mapper = Jackson2ObjectMapperBuilder.json().build();
    var page = new GasAudit.HistoryPage(key, List.of(item), 1, 0, 10);
    JsonNode json = mapper.valueToTree(page);
    assertThat(json.get("key").get("type").asText()).isEqualTo("NON_APPRAISED");
    assertThat(json.get("items").get(0).get("eventId").asText()).isEqualTo("W:301:3");
    assertThat(json.get("items").get(0).get("eventDate").asText()).isEqualTo("2030-01-01T12:34:56");
    for (String property : List.of("rateId", "userId", "value", "comment")) {
      assertThat(json.get("items").get(0).get(property).isNull()).as(property).isTrue();
    }
    assertThat(mapper.treeToValue(json, GasAudit.HistoryPage.class)).isEqualTo(page);
  }

  @Test
  void historyPageCopiesItsRowsAndRejectsOtherFamiliesOrInvalidBounds() {
    var rows = new ArrayList<>(List.of(item));
    var page = new GasAudit.HistoryPage(key, rows, 1, 0, 10);
    rows.clear();
    assertThat(page.items()).containsExactly(item);
    assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new GasAudit.HistoryPage(new GasAppraisal.Key(GasAppraisal.WorksheetType.HISTORIC, "123"),
        List.of(), 0, 0, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAudit.HistoryPage(key, List.of(), 0, -1, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAudit.HistoryPage(key, List.of(), 0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAudit.HistoryPage(key, List.of(item), 0, 0, 10)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new GasAudit.HistoryPage(key, java.util.Collections.nCopies(11, item), 11, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
