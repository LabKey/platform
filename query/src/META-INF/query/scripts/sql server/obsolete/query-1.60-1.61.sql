CREATE TABLE query.DbUserSchema (
    DbUserSchemaId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy int NULL,
    Modified DATETIME NULL,
    ModifiedBy int NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    UserSchemaName NVARCHAR(50) NOT NULL,
    DbSchemaName NVARCHAR(50) NULL,
    DbContainer UNIQUEIDENTIFIER NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container,UserSchemaName)
);