package ca.bc.gov.nrs.taps.read.oracle;

/**
 * Provisional FTA ownership: the S-link client, else the A-link client, plus the mark's district and
 * its ORG_UNIT region. The displayed region still comes from PFU.
 */
final class FtaScopeSql {
  private FtaScopeSql() {}

  /** Bind licence and mark first (either may be null, but not both), then the scope values. */
  static String scopedCtes(ReadScopePredicate scope) {
    return """
        WITH requested_input AS (
          SELECT CAST(? AS VARCHAR2(10)) AS FOREST_FILE_ID,
                 CAST(? AS VARCHAR2(6)) AS TIMBER_MARK
            FROM DUAL
        ), requested_files AS (
          SELECT I.FOREST_FILE_ID FROM requested_input I WHERE I.FOREST_FILE_ID IS NOT NULL
          UNION
          SELECT HA.FOREST_FILE_ID
            FROM HAULING_AUTHORITY HA CROSS JOIN requested_input I
           WHERE I.FOREST_FILE_ID IS NULL AND HA.TIMBER_MARK = I.TIMBER_MARK
          UNION
          SELECT BRM.FOREST_FILE_ID
            FROM BLANKET_ROAD_MARK BRM CROSS JOIN requested_input I
           WHERE I.FOREST_FILE_ID IS NULL AND BRM.TIMBER_MARK = I.TIMBER_MARK
        ), mark_parents AS (
          SELECT 'PERMIT' AS MARK_FAMILY, HA.FOREST_FILE_ID, HA.TIMBER_MARK,
                 HVA.HVA_SKEY, HVA.CUTTING_PERMIT_ID, HVA.FOREST_DISTRICT,
                 HVA.FOREST_FILE_ID AS PFU_FILE_ID,
                 HVA.HARVEST_AUTH_STATUS_CODE AS MARK_STATUS,
                 HVA.EXPIRY_DATE, HVA.EXTEND_DATE
            FROM HAULING_AUTHORITY HA
            JOIN requested_files F ON F.FOREST_FILE_ID = HA.FOREST_FILE_ID
            CROSS JOIN requested_input I
            LEFT JOIN HARVESTING_HAULING_XREF HHX ON HHX.TIMBER_MARK = HA.TIMBER_MARK
            LEFT JOIN HARVESTING_AUTHORITY HVA ON HVA.HVA_SKEY = HHX.HVA_SKEY
           WHERE I.TIMBER_MARK IS NULL OR HA.TIMBER_MARK = I.TIMBER_MARK
          UNION ALL
          SELECT 'PRIVATE', PMC.FOREST_FILE_ID, PMC.TIMBER_MARK,
                 NULL, NULL, PMC.FOREST_DISTRICT, PMC.FOREST_FILE_ID,
                 PMC.PRIVATE_MARK_STATUS_CODE,
                 PMC.PRIVATE_MARK_EXPIRY_DATE, PMC.PRIVATE_MARK_EXTEND_DATE
            FROM PRIVATE_MARK_CERTIFICATE PMC
            JOIN HAULING_AUTHORITY HA ON HA.TIMBER_MARK = PMC.TIMBER_MARK
            JOIN requested_files F ON F.FOREST_FILE_ID = PMC.FOREST_FILE_ID
            CROSS JOIN requested_input I
           WHERE I.TIMBER_MARK IS NULL OR PMC.TIMBER_MARK = I.TIMBER_MARK
          UNION ALL
          SELECT 'ROAD', BRM.FOREST_FILE_ID, BRM.TIMBER_MARK,
                 NULL, NULL, BRM.FOREST_DISTRICT, BRM.FOREST_FILE_ID,
                 NULL, NULL, NULL
            FROM BLANKET_ROAD_MARK BRM
            JOIN requested_files F ON F.FOREST_FILE_ID = BRM.FOREST_FILE_ID
            CROSS JOIN requested_input I
           WHERE I.TIMBER_MARK IS NULL OR BRM.TIMBER_MARK = I.TIMBER_MARK
        ), current_clients AS (
          SELECT F.FOREST_FILE_ID,
                 COALESCE(
                   (SELECT DISTINCT C.CLIENT_NUMBER FROM FOREST_FILE_CLIENT C
                     WHERE C.FOREST_FILE_ID = F.FOREST_FILE_ID
                       AND C.FOREST_FILE_CLIENT_TYPE_CODE = 'S'
                       AND C.CLIENT_NUMBER IS NOT NULL),
                   (SELECT DISTINCT C.CLIENT_NUMBER FROM FOREST_FILE_CLIENT C
                     WHERE C.FOREST_FILE_ID = F.FOREST_FILE_ID
                       AND C.FOREST_FILE_CLIENT_TYPE_CODE = 'A'
                       AND C.CLIENT_NUMBER IS NOT NULL)
                 ) AS CLIENT_NUMBER
            FROM requested_files F
        ), record_scope AS (
          SELECT P.*, C.CLIENT_NUMBER,
                 DISTRICT.ORG_UNIT_CODE AS ADMIN_DISTRICT_CODE,
                 REGION.ORG_UNIT_CODE AS ROLLUP_REGION_CODE,
                 DISTRICT.ORG_UNIT_NO AS DISTRICT_ROW_ID,
                 DISTRICT.ORG_UNIT_NAME AS FOREST_DISTRICT_NAME
            FROM mark_parents P
            LEFT JOIN current_clients C ON C.FOREST_FILE_ID = P.FOREST_FILE_ID
            LEFT JOIN ORG_UNIT DISTRICT ON DISTRICT.ORG_UNIT_NO = P.FOREST_DISTRICT
            LEFT JOIN ORG_UNIT REGION ON REGION.ORG_UNIT_NO = DISTRICT.ROLLUP_REGION_NO
        ), scoped_parents AS (
          SELECT * FROM record_scope WHERE %s
        )
        """.formatted(scope.sql());
  }

  // Match the legacy append-and-strip permit formatting, including repeated/null permit entries.
  // Only permits within the caller's scope are listed.
  static final String INFORMATION_SELECT = """
      , permit_lists AS (
        SELECT TIMBER_MARK,
               LISTAGG(CUTTING_PERMIT_ID || ', ', '')
                 WITHIN GROUP (ORDER BY CUTTING_PERMIT_ID) AS PERMITS_WITH_SEPARATOR
          FROM scoped_parents
         WHERE MARK_FAMILY = 'PERMIT' AND HVA_SKEY IS NOT NULL
         GROUP BY TIMBER_MARK
      )
      SELECT DISTINCT P.CLIENT_NUMBER,
             FC.CLIENT_NAME,
             P.FOREST_FILE_ID,
             CASE WHEN P.MARK_FAMILY = 'PERMIT'
                  THEN SUBSTR(CP.PERMITS_WITH_SEPARATOR, 1, LENGTH(CP.PERMITS_WITH_SEPARATOR) - 2)
                  ELSE NULL END AS CUT_PERMIT_IDS,
             PFU.FILE_TYPE_CODE,
             P.TIMBER_MARK,
             DISPLAY_REGION.ORG_UNIT_NAME AS FOREST_REGION_NAME,
             P.FOREST_DISTRICT_NAME,
             P.EXPIRY_DATE,
             P.EXTEND_DATE,
             TFSC.DESCRIPTION AS LICENCE_STATUS_DESC
        FROM scoped_parents P
        JOIN PROV_FOREST_USE PFU ON PFU.FOREST_FILE_ID = P.PFU_FILE_ID
        JOIN TENURE_FILE_STATUS_CODE TFSC
          ON TFSC.TENURE_FILE_STATUS_CODE = PFU.FILE_STATUS_ST
        JOIN ORG_UNIT DISPLAY_REGION ON DISPLAY_REGION.ORG_UNIT_NO = PFU.FOREST_REGION
        LEFT JOIN FOREST_CLIENT FC ON FC.CLIENT_NUMBER = P.CLIENT_NUMBER
        LEFT JOIN permit_lists CP ON CP.TIMBER_MARK = P.TIMBER_MARK
       WHERE P.DISTRICT_ROW_ID IS NOT NULL
         AND (
           (P.MARK_FAMILY = 'PERMIT' AND P.HVA_SKEY IS NOT NULL
             AND EXISTS (SELECT 1 FROM HARVEST_AUTH_STATUS_CODE HASC
                          WHERE HASC.HARVEST_AUTH_STATUS_CODE = P.MARK_STATUS))
           OR (P.MARK_FAMILY = 'PRIVATE'
             AND EXISTS (SELECT 1 FROM PRIVATE_MARK_STATUS_CODE PMSC
                          WHERE PMSC.PRIVATE_MARK_STATUS_CODE = P.MARK_STATUS))
           OR P.MARK_FAMILY = 'ROAD'
         )
      """;
}
