
-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId INT4 NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);


CREATE TABLE study.ParticipantDataset
(
	Container ENTITYID NOT NULL,
	ParticipantId INT4 NOT NULL,
	VisitId INT4 NULL,
	DatasetId INT4 NOT NULL,
	URI varchar(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (URI),
	CONSTRAINT AK_ParticipantDataset UNIQUE (Container, DatasetId, VisitId, ParticipantId)
);
CLUSTER  AK_ParticipantDataset ON study.ParticipantDataset;

-- two indexes for query optimization
CREATE INDEX IDX_ParticipantDatasetByVisit ON study.ParticipantDataset (Container, DatasetId, VisitId, ParticipantId, URI);
CREATE INDEX IDX_ParticipantDatasetByParticipant ON study.ParticipantDataset (Container, ParticipantId, DatasetId, VisitId, URI);