package ca.bc.gov.nrs.taps.read.oracle;

import ca.bc.gov.nrs.taps.domain.AppraisalMethod;
import ca.bc.gov.nrs.taps.domain.LegacyIdentifiers;
import ca.bc.gov.nrs.taps.read.CodeOption;
import ca.bc.gov.nrs.taps.read.EcasReference;
import ca.bc.gov.nrs.taps.security.TapsUser;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Coast/Interior reference fields, including FTAS defaults, authorized on ADS ownership. */
public final class OracleEcasReference {
  private final JdbcTemplate jdbc;

  public OracleEcasReference(JdbcTemplate jdbc) {
    this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
  }

  public Optional<EcasReference.Coast> coast(TapsUser user, String ecasId) {
    return read(user, ecasId, AppraisalMethod.C).map(reference -> {
      Parent p = reference.parent();
      List<EcasReference.TimberMark> primaries = reference.marks().stream()
          .filter(mark -> Boolean.TRUE.equals(mark.primary())).toList();
      if (primaries.size() != 1 || !primaries.getFirst().timberMark().equals(p.selectedMark())) {
        throw new DataIntegrityViolationException("coast reference requires one primary timber mark");
      }
      return new EcasReference.Coast(
          p.header(), p.selectedMark(), reference.marks(), p.referenceMark(), p.netCruiseVolume(),
          p.netMerchantableArea(), p.initialMerchantableArea(), p.pointOfAppraisalDistance(),
          p.majorCentreCode(), p.majorCentreDistance());
    });
  }

  public Optional<EcasReference.Interior> interior(TapsUser user, String ecasId) {
    return read(user, ecasId, AppraisalMethod.I).map(reference -> {
      if (reference.marks().size() != 1) {
        throw new IncorrectResultSizeDataAccessException(1, reference.marks().size());
      }
      Parent p = reference.parent();
      EcasReference.TimberMark mark = reference.marks().getFirst();
      if (!mark.timberMark().equals(p.selectedMark())) {
        throw new DataIntegrityViolationException("interior reference timber mark does not match");
      }
      return new EcasReference.Interior(
          p.header(), mark.timberMark(), mark.revisionCount(), p.referenceMark(),
          p.pointOfAppraisal(), p.sellingPriceZoneCode(), p.comparativeCruise(), p.salvage());
    });
  }

  private Optional<Reference> read(TapsUser user, String ecasId, AppraisalMethod method) {
    String id = LegacyIdentifiers.requiredId(ecasId);
    EcasReadPredicate scope = EcasReadPredicate.forUser(user);
    return jdbc.query(
        EcasReferenceSql.select(scope),
        statement -> {
          statement.setLong(1, Long.parseLong(id));
          statement.setString(2, method.name());
          for (int index = 0; index < scope.parameters().size(); index++) {
            statement.setString(index + 3, scope.parameters().get(index));
          }
        },
        rows -> {
          return extract(rows, method);
        });
  }

  private static Optional<Reference> extract(ResultSet rows, AppraisalMethod method)
      throws SQLException {
    Parent parent = null;
    List<EcasReference.TimberMark> marks = new ArrayList<>();
    while (rows.next()) {
      switch (rows.getInt("ROW_KIND")) {
        case 0 -> {
          if (parent != null) {
            throw new IncorrectResultSizeDataAccessException(1, 2);
          }
          parent = Parent.read(rows);
          if (parent.header().appraisalMethod() != method) {
            throw new DataIntegrityViolationException("reference appraisal method does not match");
          }
        }
        case 1 -> {
          String mark = rows.getString("TIMBER_MARK");
          if (mark == null) {
            throw new DataIntegrityViolationException("reference timber mark is missing");
          }
          marks.add(new EcasReference.TimberMark(
              mark, rows.getBigDecimal("MARK_CRUISE_VOLUME"),
              indicator(rows, "PRIMARY_MARK_IND"), rows.getObject("MARK_REVISION_COUNT", Integer.class)));
        }
        default -> throw new DataIntegrityViolationException("unknown reference row kind");
      }
    }
    if (parent == null) {
      if (!marks.isEmpty()) {
        throw new DataIntegrityViolationException("reference marks have no scoped parent");
      }
      return Optional.empty();
    }
    return Optional.of(new Reference(parent, List.copyOf(marks)));
  }

  private record Reference(Parent parent, List<EcasReference.TimberMark> marks) {}

  private record Parent(
      EcasReference.Header header, String selectedMark, String referenceMark,
      BigDecimal netCruiseVolume, BigDecimal netMerchantableArea, BigDecimal initialMerchantableArea,
      BigDecimal pointOfAppraisalDistance, String majorCentreCode, BigDecimal majorCentreDistance,
      CodeOption pointOfAppraisal, String sellingPriceZoneCode, Boolean comparativeCruise,
      Boolean salvage) {
    static Parent read(ResultSet row) throws SQLException {
      return new Parent(
          new EcasReference.Header(
              row.getString("ECAS_ID"), AppraisalMethod.valueOf(row.getString("APPRAISAL_METHOD_CODE")),
              row.getObject("REVISION_COUNT", Integer.class), row.getString("LICENCE"),
              row.getString("CUTTING_PERMIT"), row.getString("CLIENT_NUMBER"),
              row.getString("CLIENT_LOCN_CODE"), row.getString("LICENSEE_NAME"),
              option(row, "STATUS_CODE", "STATUS_DESCRIPTION"),
              row.getString("APPRAISAL_CATEGORY_CODE"), row.getString("REAPPRAISAL_REASON_CODE"),
              row.getString("RATE_CALC_METHOD_CODE"), localDate(row, "APPRAISAL_EFFECTIVE_DATE"),
              localDate(row, "APPRAISAL_EXPIRY_DATE"),
              option(row, "DISPLAY_ADMIN_CODE", "DISPLAY_ADMIN_NAME"),
              option(row, "GEO_DISTRICT_CODE", "GEO_DISTRICT_NAME"),
              option(row, "FILE_TYPE_CODE", "FILE_TYPE_DESCRIPTION"),
              option(row, "TSA_CODE", "TSA_DESCRIPTION"), option(row, "TSB_CODE", "TSB_DESCRIPTION"),
              option(row, "CONIF_STAND_RATE_ELIG_CODE", "CONIF_STAND_RATE_ELIG_DESCRIPTION"),
              option(row, "DECID_STAND_RATE_ELIG_CODE", "DECID_STAND_RATE_ELIG_DESCRIPTION")),
          row.getString("REFERENCE_TIMBER_MARK"), row.getString("REFERENCE_MARK"),
          row.getBigDecimal("NET_CRUISE_VOLUME"), row.getBigDecimal("NET_MERCHANTABLE_AREA"),
          row.getBigDecimal("INITIAL_MERCHANTABLE_AREA"),
          row.getBigDecimal("POINT_OF_APPRAISAL_DISTANCE"), row.getString("MAJOR_CENTRE_CODE"),
          row.getBigDecimal("ADS_LOCATION_DISTANCE_AVERAGE"),
          option(row, "POINT_OF_APPRAISAL_CODE", "POINT_OF_APPRAISAL_DESCRIPTION"),
          row.getString("SELLING_PRICE_ZONE_CODE"), indicator(row, "COMPARATIVE_CRUISE_IND"),
          indicator(row, "SALVAGE_IND"));
    }
  }

  private static CodeOption option(ResultSet row, String code, String description) throws SQLException {
    return new CodeOption(row.getString(code), row.getString(description));
  }

  private static Boolean indicator(ResultSet row, String column) throws SQLException {
    String value = row.getString(column);
    if (value == null) {
      return null;
    }
    return switch (value) {
      case "Y" -> true;
      case "N" -> false;
      default -> throw new DataIntegrityViolationException("unsupported reference indicator: " + column);
    };
  }

  private static LocalDate localDate(ResultSet row, String column) throws SQLException {
    Timestamp value = row.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime().toLocalDate();
  }
}
