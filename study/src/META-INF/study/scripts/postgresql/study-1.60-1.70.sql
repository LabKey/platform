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
ALTER TABLE study.Visit ADD COLUMN VisitDateDatasetId INT;

ALTER TABLE study.DataSet ADD COLUMN VisitDatePropertyName VARCHAR(200);

CREATE TABLE study.Plate
    (
	RowId SERIAL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created TIMESTAMP NOT NULL,
    Template BOOLEAN NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
    );


CREATE TABLE study.WellGroup
    (
	RowId SERIAL,
    PlateId INT NOT NULL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    Template BOOLEAN NOT NULL,
    TypeName VARCHAR(50) NOT NULL,
    CONSTRAINT PK_WellGroup PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroup_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    );


CREATE TABLE study.Well
    (
	RowId SERIAL,
	LSID VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
	Value FLOAT NULL,
    Dilution FLOAT NULL,
    PlateId INT NOT NULL,
    Row INT NOT NULL,
    Col INT NOT NULL,
    CONSTRAINT PK_Well PRIMARY KEY (RowId),
    CONSTRAINT FK_Well_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    );

--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD COLUMN RowId SERIAL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD COLUMN SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD COLUMN SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;
UPDATE study.Visit SET SequenceNumMin=VisitId, SequenceNumMax=VisitId;

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD COLUMN VisitRowId INT4;

UPDATE study.VisitMap
SET VisitRowId = (
    SELECT V.RowId
    FROM study.Visit V
    WHERE VisitMap.Container = V.Container AND VisitMap.VisitId = V.VisitId);

ALTER TABLE study.VisitMap DROP COLUMN VisitId;

ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);

--
-- fix up ParticipantVisit
--

CREATE TABLE study.ParticipantVisit
    (
	Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId int NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate TIMESTAMP
    );
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);


--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD COLUMN SequenceNum Numeric(15,4);
UPDATE study.StudyData SET SequenceNum=VisitId;
--ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE (Container, DatasetId, SequenceNum, ParticipantId);

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;

ALTER TABLE study.StudyData
    ADD COLUMN 	SourceLSID VARCHAR(200) NULL;

ALTER TABLE study.DataSet ADD COLUMN KeyPropertyName VARCHAR(50);             -- Property name in TypeURI

ALTER TABLE study.StudyData ADD COLUMN _key VARCHAR(200) NULL;          -- assay key column, used only on INSERT for UQ index

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;

ALTER TABLE study.studydata
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);

-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate TIMESTAMP NULL;

UPDATE study.StudyData SET _VisitDate = VisitDate;

ALTER TABLE study.StudyData DROP COLUMN VisitDate;