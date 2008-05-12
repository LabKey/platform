ALTER TABLE study.ParticipantVisit
    DROP CONSTRAINT PK_ParticipantVisit
GO

ALTER TABLE study.ParticipantVisit
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO