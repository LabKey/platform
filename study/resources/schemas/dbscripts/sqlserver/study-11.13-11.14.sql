ALTER TABLE study.ParticipantClassifications DROP COLUMN Created
GO
ALTER TABLE study.ParticipantClassifications ADD Created DATETIME
GO

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container
GO

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	ON DELETE CASCADE
GO