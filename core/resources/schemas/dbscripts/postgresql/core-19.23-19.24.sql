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
