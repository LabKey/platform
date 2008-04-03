DROP VIEW exp.StudyData;

CREATE VIEW exp.StudyData AS
    SELECT RowId, Container, StudyDatasetId, StudyParticipantId, StudyVisitId, LSID
    FROM exp.Data
    WHERE StudyDatasetId IS NOT NULL;

