CREATE TABLE study.Cohort
    (
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
    );

ALTER TABLE study.Dataset
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

ALTER TABLE study.Participant
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);

ALTER TABLE study.Visit
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Study
    ADD COLUMN ParticipantCohortDataSetId INT NULL,
    ADD COLUMN ParticipantCohortProperty VARCHAR(200) NULL;
