/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
-- Change visit ids to type numeric:
ALTER TABLE study.Visit
    DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.Visit
    ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,VisitId);

ALTER TABLE study.VisitMap
    DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitId,DataSetId);

ALTER TABLE study.studydata
    DROP CONSTRAINT AK_StudyData;
ALTER TABLE study.studydata
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_StudyData UNIQUE (Container, DatasetId, VisitId, ParticipantId);

ALTER TABLE study.Specimen
    ALTER COLUMN VisitValue TYPE NUMERIC(15,4);

CREATE TABLE study.UploadLog
(
  RowId SERIAL,
  Container ENTITYID NOT NULL,
  Created TIMESTAMP NOT NULL,
  CreatedBy USERID NOT NULL,
  Description TEXT,
  FilePath VARCHAR(512),
  DatasetId INT NOT NULL,
  Status VARCHAR(20),
  CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
  CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
);

ALTER TABLE study.Report
    ADD COLUMN ShowWithDataset INT NULL;
