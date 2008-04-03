CREATE TABLE study.Cohort
    (
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
    )
GO

ALTER TABLE study.Dataset ADD
    CohortId INT NULL,
    CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId)
GO

ALTER TABLE study.Participant ADD
    CohortId INT NULL,
    CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
GO

CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);
GO

ALTER TABLE study.Visit ADD
    CohortId INT NULL,
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);
GO

ALTER TABLE study.Study ADD
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty NVARCHAR(200) NULL;
GO