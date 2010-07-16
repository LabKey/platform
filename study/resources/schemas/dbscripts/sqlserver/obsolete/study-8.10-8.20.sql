/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
/* study-8.10-8.11.sql */

ALTER TABLE study.ParticipantVisit
    DROP CONSTRAINT PK_ParticipantVisit
GO

ALTER TABLE study.ParticipantVisit
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO

/* study-8.11-8.12.sql */

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

/* study-8.12-8.13.sql */

ALTER TABLE study.specimen ADD CurrentLocation INT;
ALTER TABLE study.specimen ADD
    CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId)
GO

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);
GO

/* study-8.13-8.14.sql */

ALTER TABLE study.Dataset ADD
    KeyPropertyManaged BIT DEFAULT 0
GO

/* study-8.14-8.15.sql */

ALTER TABLE study.Study ADD
    DatasetRowsEditable BIT DEFAULT 0
GO

/* study-8.15-8.16.sql */

UPDATE study.Study
SET DatasetRowsEditable = 0
WHERE
DatasetRowsEditable IS NULL
GO

UPDATE study.Dataset
SET KeyPropertyManaged = 0
WHERE
KeyPropertyManaged IS NULL
GO

ALTER TABLE
study.Study
ALTER COLUMN DatasetRowsEditable BIT NOT NULL
GO

ALTER TABLE
study.Dataset
ALTER COLUMN KeyPropertyManaged BIT NOT NULL
GO

/* study-8.16-8.17.sql */

