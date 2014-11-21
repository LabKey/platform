-- An empty stored procedure (similar to executeJavaUpgradeCode) that, when detected by the script runner,
-- imports a tabular data file (TSV, XLSX, etc.) into the specified table.
CREATE FUNCTION core.bulkImport(text, text, text) RETURNS void AS $$
    DECLARE note TEXT := 'Empty function that signals script runner to bulk import a file into a table.';
    BEGIN
    END
$$ LANGUAGE plpgsql;

