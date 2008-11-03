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
/* study-8.20-8.21.sql */

ALTER TABLE study.Study
    ADD COLUMN SecurityType VARCHAR(32);

UPDATE study.Study
  SET SecurityType = 'ADVANCED'
  WHERE
  StudySecurity = TRUE;

UPDATE study.Study
  SET SecurityType = 'EDITABLE_DATASETS'
  WHERE
  StudySecurity = FALSE AND
  DatasetRowsEditable = TRUE;

UPDATE study.Study
  SET SecurityType = 'BASIC'
  WHERE
  StudySecurity = FALSE AND
  DatasetRowsEditable = FALSE;

ALTER TABLE study.Study
  DROP COLUMN StudySecurity;

ALTER TABLE study.Study
  DROP COLUMN DatasetRowsEditable;

ALTER TABLE study.Study
  ALTER COLUMN SecurityType SET NOT NULL;

/* study-8.21-8.22.sql */

ALTER TABLE study.Cohort
    ADD COLUMN LSID VARCHAR(200);

/* study-8.22-8.23.sql */

UPDATE study.Cohort
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Cohort
  ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Visit
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Visit
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Visit
  ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Study
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Study
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Study
  ALTER COLUMN LSID SET NOT NULL;

/* study-8.23-8.24.sql */

ALTER TABLE study.Study
    ADD COLUMN manualCohortAssignment boolean NOT NULL DEFAULT FALSE;

/* study-8.24-8.25.sql */

CREATE TABLE study.QCState
    (
    RowId SERIAL,
    Label VARCHAR(64) NULL,
    Description VARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BOOLEAN NOT NULL,
    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
    );

ALTER TABLE study.StudyData
    ADD COLUMN QCState INT NULL,
    ADD CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId);

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState);

ALTER TABLE study.Study
    ADD DefaultPipelineQCState INT,
    ADD DefaultAssayQCState INT,
    ADD DefaultDirectEntryQCState INT,
    ADD ShowPrivateDataByDefault BOOLEAN NOT NULL DEFAULT False,
    ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId);

/* study-8.25-8.26.sql */

ALTER TABLE study.Visit
    DROP COLUMN LSID;

/* study-8.26-8.27.sql */

UPDATE study.Study
  SET SecurityType = 'BASIC_READ'
  WHERE
  SecurityType = 'BASIC';

UPDATE study.Study
  SET SecurityType = 'BASIC_WRITE'
  WHERE
  SecurityType = 'EDITABLE_DATASETS';

UPDATE study.Study
  SET SecurityType = 'ADVANCED_READ'
  WHERE
  SecurityType = 'ADVANCED';

/* study-8.27-8.28.sql */

CREATE TABLE study.SpecimenComment
       (
       RowId SERIAL,
       Container ENTITYID NOT NULL,
       SpecimenNumber VARCHAR(50) NOT NULL,
       GlobalUniqueId VARCHAR(50) NOT NULL,
       CreatedBy USERID,
       Created TIMESTAMP,
       ModifiedBy USERID,
       Modified TIMESTAMP,
       Comment TEXT,
       CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
       );

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);

/* study-8.28-8.29.sql */

UPDATE study.dataset
  SET label = name
WHERE
  label IS NULL;

ALTER TABLE study.dataset
  ALTER COLUMN label SET NOT NULL;

ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetLabel UNIQUE (container, label);

/* study-8.29-8.291.sql */

ALTER TABLE study.Specimen
  ADD COLUMN SpecimenHash VARCHAR(256);

DROP INDEX study.IX_SpecimenComment_SpecimenNumber;

ALTER TABLE study.SpecimenComment
    ADD COLUMN SpecimenHash VARCHAR(256),
    DROP COLUMN SpecimenNumber;

CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);
CREATE INDEX IX_Specimen_SpecimenHash ON study.Specimen(Container, SpecimenHash);