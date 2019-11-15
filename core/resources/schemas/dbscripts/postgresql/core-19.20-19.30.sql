-- This empty stored procedure is a synonym for core.executeJavaUpgradeCode(), but is meant to denote Java code that is used to
-- initialize data in a schema (e.g., pre-populating a table with values), not transform existing data. We mark these cases with
-- a different procedure name because our bootstrap scripts still need to invoke them, as opposed to invocations of upgrade code
-- which we remove from bootstrap scripts. See implementations of the UpgradeCode interface to find the initialization code.
CREATE FUNCTION core.executeJavaInitializationCode(text) RETURNS void AS $$
DECLARE note TEXT := 'Empty function that signals script runner to execute Java initialization code. See implementations of UpgradeCode.java.';
BEGIN
END
$$ LANGUAGE plpgsql;

CREATE TABLE core.AuthenticationConfigurations
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,

    Provider VARCHAR(64) NOT NULL,
    Description VARCHAR(255) NOT NULL,
    Enabled BOOLEAN NOT NULL,
    AutoRedirect BOOLEAN NOT NULL DEFAULT FALSE,
    SortOrder INTEGER NOT NULL DEFAULT 0,
    Properties TEXT,
    EncryptedProperties TEXT,

    CONSTRAINT PK_AuthenticationConfigurations PRIMARY KEY (RowId)
);

SELECT core.executeJavaUpgradeCode('migrateAuthenticationConfigurations');