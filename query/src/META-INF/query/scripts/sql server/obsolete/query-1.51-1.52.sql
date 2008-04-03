exec sp_addapprole 'query', 'password';

CREATE TABLE query.QueryDef (
	QueryDefId INT IDENTITY(1, 1) NOT NULL,
	EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL ,
    CreatedBy INT NULL ,
    Modified DATETIME NULL ,
    ModifiedBy INT NULL ,

	Container uniqueidentifier NOT NULL,
	Name NVARCHAR(50) NOT NULL,
	"Schema" NVARCHAR(50) NOT NULL,
    Sql NTEXT,
    MetaData NTEXT,
	Description NTEXT,
	SchemaVersion FLOAT NOT NULL,
    Flags INT NOT NULL,
    CONSTRAINT PK_QueryDef PRIMARY KEY (QueryDefId),
    CONSTRAINT UQ_QueryDef UNIQUE (Container, "Schema", Name)
    );

CREATE TABLE query.CustomView (
    CustomViewId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
	"Schema" NVARCHAR(50) NOT NULL,
	QueryName NVARCHAR(50) NOT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(50) NULL,
    CustomViewOwner INT NULL,
    Columns NTEXT,
    Filter NTEXT,
    Flags INT NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, "Schema", QueryName, CustomViewOwner, Name)
    );