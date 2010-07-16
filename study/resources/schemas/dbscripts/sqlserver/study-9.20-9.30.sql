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

ALTER TABLE study.Visit ADD ChronologicalOrder INTEGER NOT NULL DEFAULT 0
GO

ALTER TABLE study.ParticipantVisit ADD
    CohortID INT NULL,
    CONSTRAINT FK_ParticipantVisit_Cohort FOREIGN KEY (CohortID) REFERENCES study.Cohort (RowId)
GO

ALTER TABLE study.Participant DROP CONSTRAINT FK_Participant_Cohort
GO
EXEC sp_rename 'study.Participant.CohortId', 'CurrentCohortId', 'COLUMN'
GO
ALTER TABLE study.Participant ADD InitialCohortId INTEGER
GO

UPDATE study.Participant SET InitialCohortId = CurrentCohortId
GO

CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId)
GO

CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId)
GO

ALTER TABLE study.Study ADD AdvancedCohorts BIT NOT NULL DEFAULT 0
GO

/* study-9.21-9.22.sql */

ALTER TABLE study.Study ADD
    ParticipantCommentDataSetId INT NULL,
    ParticipantCommentProperty NVARCHAR(200) NULL,
    ParticipantVisitCommentDataSetId INT NULL,
    ParticipantVisitCommentProperty NVARCHAR(200) NULL;
GO

/* study-9.22-9.23.sql */

ALTER TABLE study.ParticipantVisit ADD
    ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.ParticipantVisit SET ParticipantSequenceKey = ParticipantID + '|' + CAST(SequenceNum AS NVARCHAR)
GO

ALTER TABLE study.ParticipantVisit ADD
    CONSTRAINT UQ_StudyData_ParticipantSequenceKey UNIQUE (ParticipantSequenceKey, Container)
GO

CREATE INDEX IX_ParticipantVisit_ParticipantSequenceKey ON study.ParticipantVisit(ParticipantSequenceKey, Container)
GO

ALTER TABLE study.StudyData ADD ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.StudyData SET ParticipantSequenceKey = ParticipantID + '|' + CAST(SequenceNum AS NVARCHAR)
GO

CREATE INDEX IX_StudyData_ParticipantSequenceKey ON study.StudyData(ParticipantSequenceKey, Container)
GO

ALTER TABLE study.Specimen ADD ParticipantSequenceKey NVARCHAR(200)
GO

UPDATE study.Specimen SET ParticipantSequenceKey = PTID + '|' + CAST(VisitValue AS NVARCHAR)
GO

CREATE INDEX IX_Specimen_ParticipantSequenceKey ON study.Specimen(ParticipantSequenceKey, Container)
GO

CREATE INDEX IX_SpecimenPrimaryType_PrimaryType ON study.SpecimenPrimaryType(PrimaryType)
GO

CREATE INDEX IX_SpecimenDerivative_Derivative ON study.SpecimenDerivative(Derivative)
GO

CREATE INDEX IX_SpecimenAdditive_Additive ON study.SpecimenAdditive(Additive)
GO

/* study-9.23-9.24.sql */

ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData;
ALTER TABLE study.StudyData DROP CONSTRAINT PK_ParticipantDataset;
GO

-- convert varchar columns to nvarchar to ensure correct joins with the other nvarchar columns in the system:
ALTER TABLE study.StudyData ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL;
ALTER TABLE study.StudyData ALTER COLUMN LSID NVARCHAR(200) NOT NULL;
ALTER TABLE study.StudyData ALTER COLUMN SourceLSID NVARCHAR(200);
GO

ALTER TABLE study.StudyData ADD
    CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key),
    CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID);
GO

ALTER TABLE study.UploadLog DROP CONSTRAINT UQ_UploadLog_FilePath;
GO

-- Convert varchar columns to nvarchar to ensure correct joins with the other nvarchar columns in the system.
-- Note that we need to shorten this column from 512 to 450 characters to stay under
-- SQL Server's maximum key length of 900 bytes.
ALTER TABLE study.UploadLog ALTER COLUMN FilePath NVARCHAR(400);
ALTER TABLE study.UploadLog ALTER COLUMN Status NVARCHAR(20);
GO

ALTER TABLE study.UploadLog ADD
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath);
GO

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
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Clear out the old string values
UPDATE exp.objectproperty SET stringvalue = NULL
WHERE propertyid IN (SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Just to be safe, clean out any ones where we couldn't find the right user
DELETE FROM exp.objectproperty WHERE floatvalue IS NULL AND propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

-- Update the property descriptor so that it's now an integer and is the correct lookup
UPDATE exp.propertydescriptor SET lookupschema='core', lookupquery='users', rangeuri='http://www.w3.org/2001/XMLSchema#int' WHERE propertyid IN
(SELECT pd.propertyid FROM exp.propertydescriptor pd, exp.propertydomain pdlink, exp.domaindescriptor dd, study.dataset d
WHERE pd.propertyid=pdlink.propertyid AND pdlink.domainid = dd.domainid
AND dd.domainuri = d.typeuri AND (pd.name='RunCreatedBy' OR pd.name='Run CreatedBy') AND d.protocolid IS NOT NULL AND pd.rangeuri='http://www.w3.org/2001/XMLSchema#string')
GO

/* study-9.25-9.26.sql */

ALTER TABLE study.Specimen ADD ProcessingLocation INT
GO

UPDATE study.Specimen SET ProcessingLocation = (
    SELECT MAX(ProcessingLocation) AS ProcessingLocation FROM
        (SELECT DISTINCT SpecimenId, ProcessingLocation
         FROM study.Vial
         WHERE SpecimenId = study.Specimen.RowId) Locations
    GROUP BY SpecimenId
    HAVING COUNT(ProcessingLocation) = 1
)
GO