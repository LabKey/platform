/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* study-2.30-2.31.sql */

ALTER TABLE study.Plate ADD Type NVARCHAR(200)
GO

/* study-2.31-2.32.sql */

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
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
GO

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);
GO

ALTER TABLE study.Study ADD
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty NVARCHAR(200) NULL;
GO

/* study-2.32-2.33.sql */

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

/* study-2.33-2.34.sql */

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments NVARCHAR(200)
GO
