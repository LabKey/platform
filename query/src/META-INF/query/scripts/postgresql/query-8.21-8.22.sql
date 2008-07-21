
CREATE TABLE query.QuerySnapshotDef (
	RowId SERIAL NOT NULL,
	QueryDefId INT NULL,

	EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy int NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy int NULL,
	Container ENTITYID NOT NULL,
	Schema VARCHAR(50) NOT NULL,
	Name VARCHAR(50) NOT NULL,
    Columns TEXT,
    Filter TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

