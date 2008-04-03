CREATE TABLE study.AssayRun
    (
	RowId int IDENTITY (1, 1) NOT NULL,
    AssayType NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
    )
GO
