
CREATE PROCEDURE audit.updateSelectQueryIdentifiedData AS
BEGIN
DECLARE
    @tempName VARCHAR(100);

    SELECT @tempName = storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    EXEC ('ALTER TABLE audit.' + @tempName + ' ALTER COLUMN identifieddata NVARCHAR(MAX)');
    RETURN 0;
END
GO

EXEC audit.updateSelectQueryIdentifiedData;

DROP PROCEDURE audit.updateSelectQueryIdentifiedData;
