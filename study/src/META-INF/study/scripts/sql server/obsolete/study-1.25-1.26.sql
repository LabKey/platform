CREATE TABLE study.StudyData
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(32) NOT NULL,
	VisitId INT NULL,
	DatasetId INT NOT NULL,
	LSID VARCHAR(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID),
	CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
go
