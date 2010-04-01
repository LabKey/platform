/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
GO


-- VISIT

-- ALTER TABLE study.visit ADD name NVARCHAR(200);
-- GO
-- UPDATE study.visit SET name=COALESCE(label, CAST(rowid AS NVARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V WHERE V.container=study.visit.container AND V.name=study.visit.name)
-- GO
--
-- ALTER TABLE study.visit ALTER COLUMN name VARCHAR(200) NOT NULL
-- GO
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);
-- GO


-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
GO
UPDATE study.dataset SET name=COALESCE(label, CAST(datasetid AS NVARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D WHERE D.container=study.dataset.container AND D.name=study.dataset.name)
GO

ALTER TABLE study.dataset ALTER COLUMN name VARCHAR(200) NOT NULL
GO
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
GO

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName
GO
ALTER TABLE study.dataset ALTER COLUMN name NVARCHAR(200) NOT NULL
GO
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);
GO
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
GO

ALTER TABLE study.Dataset ADD Description NTEXT NULL
GO

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
GO
ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
	(
		Container,
		DatasetId,
		SequenceNum,
		ParticipantId,
		_key
	)
GO


ALTER TABLE study.StudyDesign
    ADD Active BIT
GO

UPDATE study.StudyDesign SET Active=1 WHERE StudyEntityId IS NOT NULL
UPDATE study.StudyDesign SET Active=0 WHERE StudyEntityId IS NULL
GO

ALTER TABLE study.StudyDesign
  ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
GO

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
GO

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
GO

UPDATE study.StudyDesign SET SourceContainer = Container
GO
