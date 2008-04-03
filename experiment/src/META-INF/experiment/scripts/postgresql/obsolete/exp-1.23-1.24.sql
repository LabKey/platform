DROP VIEW exp.StudyData;

DROP INDEX exp.IDX_StudyData_ByVisit;
DROP INDEX exp.IDX_StudyData_ByParticipant;

ALTER TABLE exp.Data DROP COLUMN StudyParticipantId;
ALTER TABLE exp.Data DROP COLUMN StudyDatasetId;
ALTER TABLE exp.Data DROP COLUMN StudyVisitId;


