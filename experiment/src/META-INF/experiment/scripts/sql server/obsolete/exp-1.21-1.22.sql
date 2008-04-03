-- ParticipantId --> VARCHAR()

DROP VIEW exp.StudyData
go

ALTER TABLE exp.Data ALTER COLUMN StudyParticipantId VARCHAR(16) NULL
go

SET ARITHABORT ON
set QUOTED_IDENTIFIER ON
go

CREATE VIEW "exp"."StudyData" WITH SCHEMABINDING AS
    SELECT "Container", "StudyDatasetId", "StudyParticipantId", "StudyVisitId", "LSID" FROM "exp"."Data" WHERE "StudyDatasetId" IS NOT NULL
go

CREATE UNIQUE CLUSTERED INDEX IDX_StudyData_ByVisit ON exp.StudyData (Container, StudyDatasetId, StudyVisitId, StudyParticipantId);
CREATE INDEX IDX_StudyData_ByParticipant ON exp.StudyData (Container, StudyDatasetId, StudyParticipantId, StudyVisitId);
go


-- index string/float properties

CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue)
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue)
go

