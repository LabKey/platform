/*
 * Copyright (c) 2009 LabKey Corporation
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

/* study-9.201-9.30.sql */

/* study-9.201-9.21.sql */

ALTER TABLE study.Visit ADD COLUMN ChronologicalOrder INTEGER NOT NULL DEFAULT 0;

ALTER TABLE study.ParticipantVisit
    ADD COLUMN CohortID INT NULL,
    ADD CONSTRAINT FK_ParticipantVisit_Cohort FOREIGN KEY (CohortID) REFERENCES study.Cohort (RowId);

ALTER TABLE study.Participant DROP CONSTRAINT FK_Participant_Cohort;
ALTER TABLE study.Participant RENAME COLUMN CohortId TO CurrentCohortId;
ALTER TABLE study.Participant ADD COLUMN InitialCohortId INTEGER;
UPDATE study.Participant SET InitialCohortId = CurrentCohortId;
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

ALTER TABLE study.Study
    ADD COLUMN AdvancedCohorts BOOLEAN NOT NULL DEFAULT FALSE;

/* study-9.21-9.22.sql */

ALTER TABLE study.Study
    ADD COLUMN ParticipantCommentDataSetId INT NULL,
    ADD COLUMN ParticipantCommentProperty VARCHAR(200) NULL,
    ADD COLUMN ParticipantVisitCommentDataSetId INT NULL,
    ADD COLUMN ParticipantVisitCommentProperty VARCHAR(200) NULL;

/* study-9.22-9.23.sql */

ALTER TABLE study.ParticipantVisit
    ADD ParticipantSequenceKey VARCHAR(200);

UPDATE study.ParticipantVisit SET ParticipantSequenceKey = ParticipantID || '|' || CAST(SequenceNum AS VARCHAR);

ALTER TABLE study.ParticipantVisit
  ADD CONSTRAINT UQ_StudyData_ParticipantSequenceKey UNIQUE (ParticipantSequenceKey, Container);

CREATE INDEX IX_ParticipantVisit_ParticipantSequenceKey ON study.ParticipantVisit(ParticipantSequenceKey, Container);

ALTER TABLE study.StudyData
  ADD ParticipantSequenceKey VARCHAR(200);

UPDATE study.StudyData SET ParticipantSequenceKey = ParticipantID || '|' || CAST(SequenceNum AS VARCHAR);

CREATE INDEX IX_StudyData_ParticipantSequenceKey ON study.StudyData(ParticipantSequenceKey, Container);

ALTER TABLE study.Specimen ADD
  ParticipantSequenceKey VARCHAR(200);

UPDATE study.Specimen SET ParticipantSequenceKey = PTID || '|' || CAST(VisitValue AS VARCHAR);

CREATE INDEX IX_Specimen_ParticipantSequenceKey ON study.Specimen(ParticipantSequenceKey, Container);

CREATE INDEX IX_SpecimenPrimaryType_PrimaryType ON study.SpecimenPrimaryType(PrimaryType);
CREATE INDEX IX_SpecimenDerivative_Derivative ON study.SpecimenDerivative(Derivative);
CREATE INDEX IX_SpecimenAdditive_Additive ON study.SpecimenAdditive(Additive);

/* study-9.23-9.24.sql */

ALTER TABLE study.UploadLog DROP CONSTRAINT UQ_UploadLog_FilePath;

-- This PostgreSQL script corresponds to a more in-depth set of changes on the SQL Server side where a
-- number of VARCHAR columns were converted to NVARCHAR to accommodate wide characters.  As part of that
-- change, we needed to shorten this column from 512 to 450 characters to stay under
-- SQL Server's maximum key length of 900 bytes.  The change is made on PostgreSQL as well for consistency:
ALTER TABLE study.UploadLog ALTER COLUMN FilePath TYPE VARCHAR(400);

ALTER TABLE study.UploadLog ADD
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath);

/* study-9.24-9.25.sql */

-- When copying to a study, we used to copy the run's created by property as the user's display name. We should
-- copy as the user id with a lookup instead. Migrating takes a few steps

-- First, set the user ids for all of the rows
UPDATE exp.objectproperty SET typetag = 'f', floatvalue =
    (SELECT MAX(userid) FROM
        (SELECT displayname AS name, userid FROM core.users UNION
        SELECT email AS name, userid from core.users) AS x
    WHERE stringvalue = name)
WHERE propertyid IN (SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string');

-- Clear out the old string values
UPDATE exp.objectproperty SET stringvalue = NULL
WHERE propertyid IN (SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string');

-- Just to be safe, clean out any ones where we couldn't find the right user
DELETE FROM exp.objectproperty WHERE floatvalue IS NULL AND propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string');

-- Update the property descriptor so that it's now an integer and is the correct lookup
UPDATE exp.propertydescriptor SET lookupschema='core', lookupquery='users', rangeuri='http://www.w3.org/2001/XMLSchema#int' WHERE propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string');

/* study-9.25-9.26.sql */

ALTER TABLE study.Specimen ADD ProcessingLocation INT;

UPDATE study.Specimen SET ProcessingLocation = (
    SELECT MAX(ProcessingLocation) AS ProcessingLocation FROM
        (SELECT DISTINCT SpecimenId, ProcessingLocation
         FROM study.Vial
         WHERE SpecimenId = study.Specimen.RowId) Locations
    GROUP BY SpecimenId
    HAVING COUNT(ProcessingLocation) = 1
);