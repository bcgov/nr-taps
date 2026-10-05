package ca.bc.gov.nrs.taps.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record DateRange(
    @JsonDeserialize(using = DateRange.JsonDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate from,
    @JsonDeserialize(using = DateRange.JsonDateDeserializer.class)
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate to) {
  public DateRange {
    checkYear(from);
    checkYear(to);
    if (from != null && to != null && to.isBefore(from)) {
      throw new IllegalArgumentException("to date must be on or after from date");
    }
  }

  public boolean hasBound() {
    return from != null || to != null;
  }

  public static LocalDate parse(String value) {
    String date = LegacyIdentifiers.optionalText(value, 10, "date");
    if (date == null) {
      return null;
    }
    if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
      throw new IllegalArgumentException("date must use yyyy-MM-dd");
    }
    try {
      LocalDate parsed = LocalDate.parse(date);
      checkYear(parsed);
      return parsed;
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("date must be a valid calendar date");
    }
  }

  private static void checkYear(LocalDate date) {
    if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
      throw new IllegalArgumentException("date year must be between 0001 and 9999");
    }
  }

  public static class JsonDateDeserializer extends JsonDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      if (!parser.hasToken(JsonToken.VALUE_STRING)) {
        throw JsonMappingException.from(parser, "date must be a yyyy-MM-dd string");
      }
      try {
        return parse(parser.getText());
      } catch (IllegalArgumentException e) {
        throw JsonMappingException.from(parser, "date must be a valid yyyy-MM-dd string", e);
      }
    }
  }
}
