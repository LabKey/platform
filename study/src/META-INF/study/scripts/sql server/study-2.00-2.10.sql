/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
-- VISITMAP
EXEC sp_rename 'study.visitmap.isrequired', 'required', 'COLUMN';
go


-- VISIT

-- ALTER TABLE study.visit ADD name NVARCHAR(200);
-- go
-- UPDATE study.visit SET name=COALESCE(label,cast(rowid as NVARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V where V.container=study.visit.container and V.name=study.visit.name)
-- go
--
-- ALTER TABLE study.visit ALTER COLUMN name VARCHAR(200) NOT NULL
-- go
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);
-- go


-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
go
UPDATE study.dataset SET name=COALESCE(label,cast(datasetid as NVARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D where D.container=study.dataset.container and D.name=study.dataset.name)
go

ALTER TABLE study.dataset ALTER COLUMN name VARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName
go
ALTER TABLE study.dataset ALTER COLUMN name NVARCHAR(200) NOT NULL
go
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
go
ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

ALTER TABLE study.Site ADD
    IsClinic Bit
GO

ALTER TABLE study.Specimen ADD
    OriginatingLocationId INT,
    CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId)
GO

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);
GO

ALTER TABLE study.StudyDesign ADD StudyEntityId entityid
go

ALTER TABLE study.Dataset ADD Description NTEXT NULL
go

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
go
ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
	(
		Container,
		DatasetId,
		SequenceNum,
		ParticipantId,
		_key
	)
go


ALTER TABLE study.StudyDesign
    ADD Active BIT
go

UPDATE study.StudyDesign SET Active=1 WHERE StudyEntityId IS NOT NULL
UPDATE study.StudyDesign SET Active=0 WHERE StudyEntityId IS NULL
go

ALTER TABLE study.StudyDesign
  ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
go

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
go

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
go

UPDATE study.StudyDesign SET SourceContainer = Container
go
