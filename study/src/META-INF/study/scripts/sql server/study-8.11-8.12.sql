CREATE TABLE study.ParticipantView
       (
       RowId INT IDENTITY(1,1),
       CreatedBy USERID,
       Created DATETIME,
       ModifiedBy USERID,
       Modified DATETIME,
       Container ENTITYID NOT NULL,
       Body TEXT,
       Active BIT NOT NULL,
       CONSTRAINT PK_ParticipantView PRIMARY KEY (RowId),
       CONSTRAINT FK_ParticipantView_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
       )

GO