ALTER TABLE study.StudyData ADD Created DATETIME NULL, Modified DATETIME NULL, VisitDate DATETIME NULL
GO

ALTER TABLE study.DataSet ADD EntityId ENTITYID
GO

ALTER TABLE study.Study ADD EntityId ENTITYID
GO

ALTER TABLE study.Report DROP COLUMN Created
GO

ALTER TABLE study.Report ADD Created DATETIME
GO

CREATE TABLE study.AssayRun
    (
	RowId int IDENTITY (1, 1) NOT NULL,
    AssayType NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
    )
GO
