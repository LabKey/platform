
CREATE OR REPLACE FUNCTION audit.updateSelectQueryIdentifiedData() RETURNS integer AS $BODY$

DECLARE
    tempName TEXT;
BEGIN
    SELECT INTO tempName storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    IF (tempName IS NOT NULL)
    THEN
        EXECUTE 'ALTER TABLE audit.' || tempName || ' ALTER COLUMN identifieddata TYPE TEXT';
    END IF;
    RETURN 0;
END

$BODY$
LANGUAGE plpgsql;

SELECT audit.updateSelectQueryIdentifiedData();

DROP FUNCTION audit.updateSelectQueryIdentifiedData();
