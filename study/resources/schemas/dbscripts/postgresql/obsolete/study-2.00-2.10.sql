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

ALTER TABLE study.visitmap RENAME COLUMN isrequired TO required;


-- VISIT

-- ALTER TABLE study.visit ADD COLUMN name VARCHAR(200);
--
-- UPDATE study.visit SET name=COALESCE(label, CAST(rowid AS VARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V WHERE V.container=study.visit.container AND V.name=study.visit.name)
--
-- ALTER TABLE study.visit ALTER name SET NOT NULL;
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);


-- DATASET

ALTER TABLE study.dataset ADD COLUMN name VARCHAR(200);

UPDATE study.dataset SET name=COALESCE(label, CAST(datasetid AS VARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D WHERE D.container=study.dataset.container AND D.name=study.dataset.name);

ALTER TABLE study.dataset ALTER name SET NOT NULL;
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);

ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

ALTER TABLE study.Site
    ADD IsClinic Boolean;

ALTER TABLE study.Specimen
    ADD OriginatingLocationId INT,
    ADD CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId);

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);

ALTER TABLE study.StudyDesign ADD COLUMN StudyEntityId entityid;

ALTER TABLE study.Dataset ADD COLUMN Description TEXT NULL;

--ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
--ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
--	(
--		Container,
--		DatasetId,
--		SequenceNum,
--		ParticipantId,
--		_key
--	)

ALTER TABLE study.StudyDesign
    ADD Active boolean NOT NULL DEFAULT FALSE;

UPDATE study.StudyDesign SET Active=true WHERE StudyEntityId IS NOT NULL;

ALTER TABLE study.StudyDesign
    DROP StudyEntityId,
    ADD SourceContainer ENTITYID;

UPDATE study.StudyDesign SET SourceContainer = Container;
