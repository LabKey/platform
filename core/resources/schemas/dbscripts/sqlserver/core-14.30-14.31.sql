-- An empty stored procedure (similar to executeJavaUpgradeCode) that, when detected by the script runner,
-- imports a tabular data file (TSV, XLSX, etc.) into the specified table.
CREATE PROCEDURE core.bulkImport(@schema VARCHAR(200), @table VARCHAR(200), @filename VARCHAR(200)) AS
BEGIN
    DECLARE @notice VARCHAR(255)
    SET @notice = 'Empty function that signals script runner to bulk import a file into a table.'
END;

GO
