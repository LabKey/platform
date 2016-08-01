
CREATE OR REPLACE FUNCTION audit.updateSelectQueryIdentifiedData() RETURNS integer AS $BODY$

DECLARE
    tempName TEXT;
BEGIN
    SELECT INTO tempName storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    EXECUTE 'ALTER TABLE audit.' || tempName || ' ALTER COLUMN identifieddata TYPE TEXT';
    RETURN 0;
END

$BODY$
LANGUAGE plpgsql;

SELECT audit.updateSelectQueryIdentifiedData();

DROP FUNCTION audit.updateSelectQueryIdentifiedData();
