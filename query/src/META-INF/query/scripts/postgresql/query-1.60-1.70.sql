CREATE TABLE query.DbUserSchema (
    DbUserSchemaId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy int NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy int NULL,

    Container ENTITYID NOT NULL,
    UserSchemaName VARCHAR(50) NOT NULL,
    DbSchemaName VARCHAR(50) NULL,
    DbContainer ENTITYID NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container,UserSchemaName)
);