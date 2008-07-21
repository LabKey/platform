CREATE TABLE query.QuerySnapshotDef (
	RowId INT IDENTITY(1,1) NOT NULL,
	QueryDefId INT NULL,

	EntityId ENTITYID NOT NULL,
    Created DATETIME NULL,
    CreatedBy int NULL,
    Modified DATETIME NULL,
    ModifiedBy int NULL,
	Container ENTITYID NOT NULL,
	"Schema" NVARCHAR(50) NOT NULL,
	Name NVARCHAR(50) NOT NULL,
    Columns TEXT,
    Filter TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);
GO
