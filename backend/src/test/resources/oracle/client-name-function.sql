-- TEST-ONLY fixture helper: does not reproduce or validate the production function's rules.
CREATE OR REPLACE FUNCTION SIL_GET_CLIENT_NAME(client_no IN VARCHAR2)
RETURN VARCHAR2 IS
  name_value VARCHAR2(100);
BEGIN
  SELECT CLIENT_NAME INTO name_value FROM FOREST_CLIENT WHERE CLIENT_NUMBER = client_no;
  RETURN name_value;
EXCEPTION
  WHEN NO_DATA_FOUND THEN RETURN NULL;
END;
