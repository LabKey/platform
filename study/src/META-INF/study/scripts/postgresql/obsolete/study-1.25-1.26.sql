CREATE TABLE study.StudyData
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(32) NOT NULL,
	VisitId INT4 NULL,
	DatasetId INT4 NOT NULL,
	LSID VARCHAR(200) NOT NULL,
	CONSTRAINT PK_StudyData PRIMARY KEY (LSID),
	CONSTRAINT AK_StudyData UNIQUE (Container, DatasetId, VisitId, ParticipantId)
);
CLUSTER AK_StudyData ON study.StudyData;

-- consider container,participant,dataset,visit index