ALTER TABLE exp.Data ADD StudyParticipantId INT NULL;
ALTER TABLE exp.Data ADD StudyDatasetId INT NULL;
ALTER TABLE exp.Data ADD StudyVisitId INT NULL;
go

CREATE VIEW "exp"."StudyData" WITH SCHEMABINDING AS
    SELECT "Container", "StudyDatasetId", "StudyParticipantId", "StudyVisitId", "LSID" FROM "exp"."Data" WHERE "StudyDatasetId" IS NOT NULL
go

SET ARITHABORT ON
set QUOTED_IDENTIFIER ON
go

CREATE UNIQUE CLUSTERED INDEX IDX_StudyData_ByVisit ON exp.StudyData (Container, StudyDatasetId, StudyVisitId, StudyParticipantId);
CREATE INDEX IDX_StudyData_ByParticipant ON exp.StudyData (Container, StudyDatasetId, StudyParticipantId, StudyVisitId);
go
