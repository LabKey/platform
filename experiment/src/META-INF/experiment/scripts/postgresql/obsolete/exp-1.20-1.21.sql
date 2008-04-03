ALTER TABLE exp.Data ADD COLUMN StudyParticipantId INT4 NULL;
ALTER TABLE exp.Data ADD COLUMN StudyDatasetId INT4 NULL;
ALTER TABLE exp.Data ADD COLUMN StudyVisitId INT4 NULL;

CREATE VIEW exp.StudyData AS
    SELECT * FROM exp.Data WHERE StudyDatasetId IS NOT NULL;

CREATE UNIQUE INDEX IDX_StudyData_ByVisit ON exp.Data (Container, StudyDatasetId, StudyVisitId, StudyParticipantId)
    WHERE StudyDatasetId IS NOT NULL;
CREATE UNIQUE INDEX IDX_StudyData_ByParticipant ON exp.Data (Container, StudyDatasetId, StudyParticipantId, StudyVisitId)
    WHERE StudyDatasetId IS NOT NULL;
