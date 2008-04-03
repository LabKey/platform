-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId INT NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
)
go


CREATE TABLE study.ParticipantDataset
(
	Container ENTITYID NOT NULL,
	ParticipantId INT NOT NULL,
	VisitId INT NULL,
	DatasetId INT NOT NULL,
	URI varchar(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (URI),
	CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
go

-- two indexes for query optimization
CREATE INDEX IDX_ParticipantDatasetByVisit ON study.ParticipantDataset (Container, DatasetId, VisitId, ParticipantId, URI)
CREATE INDEX IDX_ParticipantDatasetByParticipant ON study.ParticipantDataset (Container, ParticipantId, DatasetId, VisitId, URI)
go