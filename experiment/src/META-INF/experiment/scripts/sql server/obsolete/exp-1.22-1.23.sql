if exists (select * from dbo.sysobjects where id = object_id(N'exp.StudyData') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.StudyData
go

SET ANSI_NULLS ON
SET QUOTED_IDENTIFIER ON
go

CREATE VIEW exp.StudyData
WITH SCHEMABINDING
AS
SELECT RowId, Container, StudyDatasetId, StudyParticipantId, StudyVisitId, LSID
FROM exp.Data
WHERE StudyDatasetId IS NOT NULL
go

CREATE UNIQUE CLUSTERED INDEX IDX_StudyData_ByVisit ON exp.StudyData (Container, StudyDatasetId, StudyVisitId, StudyParticipantId);
CREATE INDEX IDX_StudyData_ByParticipant ON exp.StudyData (Container, StudyDatasetId, StudyParticipantId, StudyVisitId);
go



