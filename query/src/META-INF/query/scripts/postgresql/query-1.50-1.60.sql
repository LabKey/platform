CREATE SCHEMA query;

SET search_path TO query, public;

CREATE TABLE query.QueryDef (
	QueryDefId SERIAL NOT NULL,
	EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL ,
    CreatedBy int NULL ,
    Modified TIMESTAMP NULL ,
    ModifiedBy int NULL ,

	Container ENTITYID NOT NULL,
	Name VARCHAR(50) NOT NULL,
	Schema VARCHAR(50) NOT NULL,
    Sql TEXT,
    MetaData TEXT,
	Description TEXT,
	SchemaVersion FLOAT8 NOT NULL,
    Flags INTEGER NOT NULL,
    CONSTRAINT PK_QueryDef PRIMARY KEY (QueryDefId),
    CONSTRAINT UQ_QueryDef UNIQUE (Container, Schema, Name)
    );

CREATE TABLE query.CustomView (
    CustomViewId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy int NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
	Schema VARCHAR(50) NOT NULL,
	QueryName VARCHAR(50) NOT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NULL,
    CustomViewOwner int NULL,
    Columns TEXT,
    Filter TEXT,
    Flags INTEGER NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, Schema, QueryName, CustomViewOwner, Name)
    );