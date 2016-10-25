/* audit-16.20-16.21.sql */

CREATE PROCEDURE audit.updateSelectQueryIdentifiedData AS
BEGIN
DECLARE
    @tempName VARCHAR(100);

    SELECT @tempName = storagetablename
        FROM exp.domaindescriptor WHERE name = 'SelectQueryAuditDomain';
    IF (@tempName IS NOT NULL)
    BEGIN
        EXEC ('ALTER TABLE audit.' + @tempName + ' ALTER COLUMN identifieddata NVARCHAR(MAX)')
    END
    RETURN 0;
END
GO

EXEC audit.updateSelectQueryIdentifiedData;

DROP PROCEDURE audit.updateSelectQueryIdentifiedData;