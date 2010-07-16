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
/* study-8.11-8.12.sql */

CREATE TABLE study.ParticipantView
       (
       RowId SERIAL,
       Container ENTITYID NOT NULL,
       CreatedBy USERID,
       Created TIMESTAMP,
       ModifiedBy USERID,
       Modified TIMESTAMP,
       Body TEXT,
       Active BOOLEAN NOT NULL,
       CONSTRAINT PK_ParticipantView PRIMARY KEY (RowId),
       CONSTRAINT FK_ParticipantView_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
       );

/* study-8.12-8.13.sql */

ALTER TABLE study.specimen
    ADD column CurrentLocation INT,
    ADD CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId);

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);

/* study-8.13-8.14.sql */

ALTER TABLE study.Dataset
    ADD COLUMN keyPropertyManaged boolean DEFAULT FALSE;

/* study-8.14-8.15.sql */

ALTER TABLE study.Study
    ADD COLUMN datasetRowsEditable boolean DEFAULT FALSE;

/* study-8.15-8.16.sql */

UPDATE study.Study
SET DatasetRowsEditable = FALSE
WHERE
DatasetRowsEditable IS NULL;

UPDATE study.Dataset
SET KeyPropertyManaged = FALSE
WHERE
KeyPropertyManaged IS NULL;

ALTER TABLE study.Study
    ALTER COLUMN DatasetRowsEditable SET NOT NULL;

ALTER TABLE study.Dataset
    ALTER COLUMN KeyPropertyManaged SET NOT NULL;
