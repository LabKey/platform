DROP TABLE study.Participant;


CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(16) NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);
