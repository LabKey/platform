ALTER TABLE study.Visit ADD COLUMN VisitDateDatasetId INT;

ALTER TABLE study.DataSet ADD COLUMN VisitDatePropertyName VARCHAR(200);


CREATE TABLE study.ParticipantVisit
    (
	Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitId NUMERIC(15,4) NOT NULL,
    VisitDate TIMESTAMP
    );
