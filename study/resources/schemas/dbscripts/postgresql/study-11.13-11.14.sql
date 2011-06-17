ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container;

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	ON DELETE CASCADE;
