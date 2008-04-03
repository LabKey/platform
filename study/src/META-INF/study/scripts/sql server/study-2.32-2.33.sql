ALTER TABLE study.Participant
    DROP CONSTRAINT PK_Participant
GO

DROP INDEX study.Participant.IX_Participant_ParticipantId
GO    

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.Participant
    ADD CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
GO    

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId)
GO    