ALTER TABLE study.Visit ADD VisitDateDatasetId INT
go
ALTER TABLE study.DataSet ADD VisitDatePropertyName NVARCHAR(200)
go

CREATE TABLE study.ParticipantVisit
    (
	Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitId NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME
    );
go