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
ALTER TABLE study.Visit ADD VisitDateDatasetId INT
go
ALTER TABLE study.DataSet ADD VisitDatePropertyName NVARCHAR(200)
go

CREATE TABLE study.Plate
    (
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    CreatedBy USERID NOT NULL,
    Created DATETIME NOT NULL,
    Template BIT NOT NULL,
    DataFileId ENTITYID,
    Rows INT NOT NULL,
    Columns INT NOT NULL,
    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
    )
GO

CREATE TABLE study.WellGroup
    (
    RowId INT IDENTITY(1,1),
    PlateId INT NOT NULL,
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NULL,
    Template BIT NOT NULL,
    TypeName NVARCHAR(50) NOT NULL,
    CONSTRAINT PK_WellGroup PRIMARY KEY (RowId),
    CONSTRAINT FK_WellGroup_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    )
GO

CREATE TABLE study.Well
    (
    RowId INT IDENTITY(1,1),
    LSID NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    Value FLOAT NULL,
    Dilution FLOAT NULL,
    PlateId INT NOT NULL,
    Row INT NOT NULL,
    Col INT NOT NULL,
    CONSTRAINT PK_Well PRIMARY KEY (RowId),
    CONSTRAINT FK_Well_Plate FOREIGN KEY (PlateId) REFERENCES study.Plate(RowId)
    )
GO

--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD RowId INT IDENTITY(1,1) NOT NULL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;
go
UPDATE study.Visit SET SequenceNumMin=VisitId, SequenceNumMax=VisitId;
go

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD VisitRowId INT NOT NULL DEFAULT -1;
go
UPDATE study.VisitMap
SET VisitRowId = (
    SELECT V.RowId
    FROM study.Visit V
    WHERE VisitMap.Container = V.Container AND VisitMap.VisitId = V.VisitId)
FROM study.VisitMap VisitMap
go
ALTER TABLE study.VisitMap DROP COLUMN VisitId;
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);
go

--
-- fix up ParticipantVisit
--

CREATE TABLE study.ParticipantVisit
    (
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId int NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME NULL
    );
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
go

--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD SequenceNum Numeric(15,4);
go
UPDATE study.StudyData SET SequenceNum=VisitId;
go
ALTER TABLE study.StudyData DROP AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, SequenceNum, ParticipantId);
go

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;
go

ALTER TABLE study.StudyData
    ADD SourceLSID VARCHAR(200) NULL
GO
ALTER TABLE study.DataSet ADD KeyPropertyName NVARCHAR(50) NULL         -- Property name in TypeURI
go

ALTER TABLE study.StudyData ADD _key NVARCHAR(200) NULL                 -- assay key column, used only on INSERT for UQ index
go

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
go

ALTER TABLE study.StudyData
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
go

-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate DATETIME NULL
go
UPDATE study.StudyData SET _VisitDate = VisitDate
go
ALTER TABLE study.StudyData DROP COLUMN VisitDate
go