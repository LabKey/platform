CREATE TABLE core.AuthenticationConfigurations
(
    RowId INT IDENTITY(1,1),
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,

    Provider NVARCHAR(64) NOT NULL,
    Description NVARCHAR(255) NOT NULL,
    Enabled BIT NOT NULL,
    AutoRedirect BIT NOT NULL DEFAULT 0,
    SortOrder INTEGER NOT NULL DEFAULT 0,
    Properties NVARCHAR(MAX),
    EncryptedProperties NVARCHAR(MAX),

    CONSTRAINT PK_AuthenticationConfigurations PRIMARY KEY (RowId)
);
