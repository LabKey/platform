/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

CREATE SCHEMA study;
CREATE SCHEMA studyDataset;
CREATE SCHEMA specimenTables;
CREATE SCHEMA studydesign;

CREATE TABLE study.Study
(
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    EntityId ENTITYID,
    TimepointType VARCHAR(15) NOT NULL,
    StartDate TIMESTAMP,
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty VARCHAR(200) NULL,
    SecurityType VARCHAR(32) NOT NULL,
    LSID VARCHAR(200) NOT NULL,
    manualCohortAssignment BOOLEAN NOT NULL DEFAULT FALSE,
    DefaultPipelineQCState INT,
    DefaultAssayQCState INT,
    DefaultDirectEntryQCState INT,
    ShowPrivateDataByDefault BOOLEAN NOT NULL DEFAULT FALSE,
    AllowReload BOOLEAN NOT NULL DEFAULT FALSE,
    ReloadInterval INT NULL,
    LastReload TIMESTAMP NULL,
    ReloadUser UserId,
    AdvancedCohorts BOOLEAN NOT NULL DEFAULT FALSE,
    ParticipantCommentDataSetId INT NULL,
    ParticipantCommentProperty VARCHAR(200) NULL,
    ParticipantVisitCommentDataSetId INT NULL,
    ParticipantVisitCommentProperty VARCHAR(200) NULL,
    SubjectNounSingular VARCHAR(50) NOT NULL DEFAULT 'Participant',
    SubjectNounPlural VARCHAR(50) NOT NULL DEFAULT 'Participants',
    SubjectColumnName VARCHAR(50) NOT NULL DEFAULT 'ParticipantId',

    CONSTRAINT PK_Study PRIMARY KEY (Container)
);

ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES core.dataStates (RowId);
ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES core.dataStates (RowId);
ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES core.dataStates (RowId);

ALTER TABLE study.Study
    ADD COLUMN BlankQCStatePublic BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE study.Study ADD Description text;
ALTER TABLE study.Study ADD ProtocolDocumentEntityId entityid;
ALTER TABLE study.Study ALTER COLUMN ProtocolDocumentEntityId SET NOT NULL;
ALTER TABLE study.Study ADD SourceStudyContainerId entityid;
ALTER TABLE study.Study ADD DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS';
ALTER TABLE study.Study ADD Investigator VARCHAR(200);
ALTER TABLE study.Study ADD StudyGrant VARCHAR(200);
ALTER TABLE study.Study RENAME COLUMN studyGrant TO "Grant";
ALTER TABLE study.study ADD COLUMN DefaultTimepointDuration INT NOT NULL DEFAULT 1;

DELETE FROM study.study WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE study.Study
    ADD CONSTRAINT FK_Study_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;

ALTER TABLE study.Study
    ADD StudySnapshot INT NULL,
    ADD LastSpecimenLoad TIMESTAMP NULL;  -- Helps determine whether a specimen refresh is needed

CREATE INDEX IX_Study_StudySnapshot ON study.Study(StudySnapshot);

ALTER TABLE study.Study ADD AllowReqLocRepository BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocClinic BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocSAL BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocEndpoint BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.study ADD ParticipantAliasDatasetName VARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasSourceColumnName VARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasColumnName VARCHAR(200);
ALTER TABLE study.study DROP ParticipantAliasDatasetName;
ALTER TABLE study.study ADD ParticipantAliasDatasetId INT;
ALTER TABLE study.study RENAME ParticipantAliasSourceColumnName TO ParticipantAliasSourceProperty;
ALTER TABLE study.study RENAME ParticipantAliasColumnName TO ParticipantAliasProperty;

-- new fields to add to existing study properties table
ALTER TABLE study.Study ADD COLUMN Species VARCHAR(200);
ALTER TABLE study.Study ADD COLUMN EndDate TIMESTAMP;
ALTER TABLE study.Study ADD COLUMN AssayPlan TEXT;

ALTER TABLE study.Study ADD COLUMN ShareDatasetDefinitions BOOLEAN NOT NULL DEFAULT false;

-- Add new skip query validation column to study.study
ALTER TABLE study.Study ADD COLUMN ValidateQueriesAfterImport BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE study.Study SET ValidateQueriesAfterImport = TRUE WHERE AllowReload = TRUE;

ALTER TABLE study.Study ADD COLUMN ShareVisitDefinitions BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE study.Cohort
(
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    LSID VARCHAR(200) NOT NULL,

    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
);

ALTER TABLE study.Cohort ADD COLUMN Enrolled BOOLEAN NOT NULL DEFAULT TRUE;

-- new fields to add to existing cohort table
ALTER TABLE study.Cohort ADD COLUMN SubjectCount INT;
ALTER TABLE study.Cohort ADD COLUMN Description TEXT;

CREATE TABLE study.Visit
(
    RowId SERIAL,
    SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0,
    SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0,
    Label VARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    Container ENTITYID NOT NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    VisitDateDatasetId INT,
    CohortId INT NULL,
    ChronologicalOrder INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT PK_Visit PRIMARY KEY (Container, RowId),
    CONSTRAINT UQ_Visit_ContSeqNum UNIQUE(Container, SequenceNumMin),
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Visit ADD SequenceNumHandling VARCHAR(32) NULL;
ALTER TABLE study.Visit ADD COLUMN Description TEXT;

-- new fields to add to existing visit table, default SequenceNumTarget to SequenceNumMin
ALTER TABLE study.Visit ADD COLUMN SequenceNumTarget NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.visit RENAME COLUMN SequenceNumTarget TO ProtocolDay;
UPDATE study.visit SV SET ProtocolDay = Round((SV.SequenceNumMax + SV.SequenceNumMin)/2)
FROM study.study SS
WHERE SS.Container = SV.Container AND SS.TimePointType = 'DATE';

ALTER TABLE study.visit ALTER COLUMN protocolday DROP NOT NULL;
ALTER TABLE study.visit ALTER COLUMN protocolday SET DEFAULT NULL;

CREATE TABLE study.VisitMap
(
    Container ENTITYID NOT NULL,
    VisitRowId INT4 NOT NULL,   -- FK
    DataSetId INT NOT NULL, -- FK
    Required BOOLEAN NOT NULL DEFAULT '1',

    CONSTRAINT PK_VisitMap PRIMARY KEY (Container, VisitRowId, DataSetId)
);

CREATE TABLE study.DataSet -- AKA CRF or Assay
(
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI VARCHAR(200) NULL,
    Label VARCHAR(200) NOT NULL,
    Category VARCHAR(200) NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    EntityId ENTITYID,
    VisitDatePropertyName VARCHAR(200),
    KeyPropertyName VARCHAR(50),             -- Property name in TypeURI
    Name VARCHAR(200) NOT NULL,
    Description TEXT NULL,
    DemographicData BOOLEAN DEFAULT FALSE,
    CohortId INT NULL,
    ProtocolId INT NULL,
    KeyManagementType VARCHAR(10)NOT NULL,

    CONSTRAINT PK_DataSet PRIMARY KEY (Container,DataSetId),
    CONSTRAINT UQ_DatasetName UNIQUE (Container, Name),
    CONSTRAINT UQ_DatasetLabel UNIQUE (Container, Label),
    CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

-- Switch case-sensitive UNIQUE CONSTRAINTs to case-insensitive UNIQUE INDEXes

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName;
ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetLabel;

CREATE UNIQUE INDEX UQ_DatasetName ON study.Dataset (Container, LOWER(Name));
CREATE UNIQUE INDEX UQ_DatasetLabel ON study.Dataset (Container, LOWER(Label));

ALTER TABLE study.Dataset ADD COLUMN CategoryId Integer;

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;
ALTER TABLE study.Dataset ADD COLUMN Modified TIMESTAMP;
ALTER TABLE study.Dataset ADD COLUMN Type VARCHAR(50) NOT NULL DEFAULT 'Standard';

-- Add new tag column to study.dataset
ALTER TABLE study.dataset ADD COLUMN Tag VARCHAR(1000);

-- used by shared dataset definitions, specifies if data is shared across folders
--   NONE: (default) data is not shared across folders, same as any other container filtered table
--   ALL:  rows are all shared, visible in all study folders containing this dataset
--   PTID: rows are all shared, and are visible if PTID is a found in study.participants for the current folder
ALTER TABLE study.dataset ADD COLUMN dataSharing VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE study.dataset ADD COLUMN UseTimeKeyField BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE study.Dataset ALTER COLUMN TypeURI TYPE VARCHAR(300);

CREATE TABLE study.SampleRequestStatus
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),
    FinalState BOOLEAN NOT NULL DEFAULT '0',
    SpecimensLocked BOOLEAN NOT NULL DEFAULT '1',

    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE TABLE study.SampleRequestActor
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),
    PerSite BOOLEAN NOT NULL DEFAULT '0',

    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE TABLE study.SampleRequest
(
    -- standard fields
    _ts TIMESTAMP,
    RowId SERIAL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,

    StatusId INT NOT NULL,
    Comments TEXT,
    DestinationSiteId INT NULL,
    Hidden BOOLEAN NOT NULL DEFAULT '0',
    EntityId ENTITYID NULL,

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId)
);

CREATE INDEX IX_SampleRequest_Container ON study.SampleRequest(Container);
CREATE INDEX IX_SampleRequest_StatusId ON study.SampleRequest(StatusId);
CREATE INDEX IX_SampleRequest_DestinationSiteId ON study.SampleRequest(DestinationSiteId);
CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);

CREATE TABLE study.SampleRequestRequirement
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description VARCHAR(300),
    Complete BOOLEAN NOT NULL DEFAULT '0',
    OwnerEntityId ENTITYID NULL,

    CONSTRAINT PK_SampleRequestRequirement PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId)
);

CREATE INDEX IX_SampleRequestRequirement_Container ON study.SampleRequestRequirement(Container);
CREATE INDEX IX_SampleRequestRequirement_RequestId ON study.SampleRequestRequirement(RequestId);
CREATE INDEX IX_SampleRequestRequirement_ActorId ON study.SampleRequestRequirement(ActorId);
CREATE INDEX IX_SampleRequestRequirement_SiteId ON study.SampleRequestRequirement(SiteId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);

CREATE TABLE study.SampleRequestEvent
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    Comments TEXT,
    EntryType VARCHAR(64),
    RequirementId INT NULL,
    
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId)
);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);
CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE TABLE study.Participant
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,
    StartDate TIMESTAMP,
    InitialCohortId INTEGER,
    CurrentCohortId INT NULL,

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);

-- TODO: These indexes are redundant... but the old create index, rename column, create index steps left us in this state
CREATE INDEX IX_Participant_CohortId ON study.Participant(CurrentCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD COLUMN DateOffset INT NOT NULL DEFAULT CAST((RANDOM() * 364 + 1) AS INT);

-- Nullable... random alternate IDs are set via code
ALTER TABLE study.Participant ADD COLUMN AlternateId VARCHAR(32) NULL;

-- Track participant indexing in the participant table now
ALTER TABLE study.Participant ADD LastIndexed TIMESTAMP NULL;

-- Add Modified column so we can actually use LastIndexed, #31139
ALTER TABLE study.Participant ADD COLUMN Modified TIMESTAMP;
UPDATE study.Participant SET Modified = CURRENT_TIMESTAMP;
CREATE TABLE study.SampleRequestSpecimen
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenGlobalUniqueId VARCHAR(100),
    Orphaned BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId)
);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);
CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);
CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE TABLE study.UploadLog
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy USERID NOT NULL,
    Description TEXT,
    FilePath VARCHAR(400),   -- Match SQL Server, where length is 400 to stay under max key length of 900 bytes
    DatasetId INT NOT NULL,
    Status VARCHAR(20),

    CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
);

CREATE TABLE study.ParticipantVisit
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId INT NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate TIMESTAMP,
    Day INT4,
    CohortID INT NULL,
    ParticipantSequenceKey VARCHAR(200),

    CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId),
    CONSTRAINT UQ_StudyData_ParticipantSequenceKey UNIQUE (ParticipantSequenceKey, Container),
    CONSTRAINT FK_ParticipantVisit_Cohort FOREIGN KEY (CohortID) REFERENCES study.Cohort (RowId)
);

CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);
CREATE INDEX IX_ParticipantVisit_ParticipantSequenceKey ON study.ParticipantVisit(ParticipantSequenceKey, Container);

-- Rename 'ParticipantSequenceKey' to 'ParticipantSequenceNum' along with constraints and indices.
ALTER TABLE study.ParticipantVisit
  RENAME ParticipantSequenceKey TO ParticipantSequenceNum;
ALTER TABLE study.ParticipantVisit
  ADD CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum UNIQUE (ParticipantSequenceNum, Container);
ALTER TABLE study.ParticipantVisit
  DROP CONSTRAINT UQ_StudyData_ParticipantSequenceKey;

ALTER INDEX study.IX_ParticipantVisit_ParticipantSequenceKey RENAME TO IX_ParticipantVisit_ParticipantSequenceNum;

-- To change the PK, it is more efficient to drop all other indexes (including unique constraints),
-- drop and recreate PK, and then rebuild indexes

-- Consider: do we need a unique constraint on ParticipantSequenceNum if we have separate ones on Participant, SequenceNum ??
ALTER TABLE study.ParticipantVisit DROP CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum;
DROP INDEX study.IX_ParticipantVisit_Container;
DROP INDEX study.IX_ParticipantVisit_ParticipantId;
DROP INDEX study.IX_ParticipantVisit_ParticipantSequenceNum;
DROP INDEX study.IX_ParticipantVisit_SequenceNum;

-- changing order of keys to make supporting index useful for Container+Participant queries
ALTER TABLE study.ParticipantVisit DROP CONSTRAINT PK_ParticipantVisit;

-- Was previously Container, SequenceNum, ParticipantId
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY
  (Container, ParticipantId, SequenceNum);

ALTER TABLE study.ParticipantVisit ADD CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum UNIQUE
  (ParticipantSequenceNum, Container);

CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit (ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit (SequenceNum);

-- clean up some bad participantsequencenum values seen in the wild
DELETE FROM Study.ParticipantVisit WHERE ParticipantSequenceNum = 'NULL';

UPDATE study.participantvisit SET visitrowid=-1 WHERE visitrowid IS NULL;
ALTER TABLE study.participantvisit ALTER COLUMN visitrowid SET DEFAULT -1;
ALTER TABLE study.participantvisit ALTER COLUMN visitrowid SET NOT NULL;

SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_ParticipantId');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_SequenceNum');

CREATE INDEX IX_PV_SequenceNum ON study.ParticipantVisit (Container, SequenceNum);

--Drop existing indexes, if they exist
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_sequencenum');
SELECT core.fn_dropifexists('ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_visitrowid');

--For Resync perf
CREATE INDEX ix_participantvisit_sequencenum ON study.participantvisit (container, participantid, sequencenum, ParticipantSequenceNum);
CREATE INDEX ix_participantvisit_visitrowid ON study.participantvisit (visitrowid);

CREATE TABLE study.StudyDesign
(
    -- standard fields
    _ts TIMESTAMP,
    StudyId SERIAL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Container ENTITYID NOT NULL,
    PublicRevision INT NULL,
    DraftRevision INT NULL,
    Label VARCHAR(200) NOT NULL,
    Active BOOLEAN NOT NULL DEFAULT FALSE,
    SourceContainer ENTITYID,

    CONSTRAINT PK_StudyDesign PRIMARY KEY (StudyId),
    CONSTRAINT UQ_StudyDesign UNIQUE (Container,StudyId),
    CONSTRAINT UQ_StudyDesignLabel UNIQUE (Container, Label)
);

UPDATE study.StudyDesign SET sourceContainer=Container WHERE sourceContainer NOT IN (SELECT entityid FROM core.containers);

CREATE TABLE study.StudyDesignVersion
(
    -- standard fields
    _ts TIMESTAMP,
    RowId SERIAL,
    StudyId INT NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,
    Revision INT NOT NULL,
    Draft BOOLEAN NOT NULL DEFAULT '1',
    Label VARCHAR(200) NOT NULL,
    Description TEXT,
    XML TEXT,

    CONSTRAINT PK_StudyDesignVersion PRIMARY KEY (StudyId,Revision),
    CONSTRAINT FK_StudyDesignVersion_StudyDesign FOREIGN KEY (StudyId) REFERENCES study.StudyDesign(StudyId),
    CONSTRAINT UQ_StudyDesignVersion UNIQUE (Container,Label,Revision)
);

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

CREATE TABLE study.SpecimenComment
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    GlobalUniqueId VARCHAR(50) NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Comment TEXT,
    SpecimenHash VARCHAR(256),
    QualityControlFlag BOOLEAN NOT NULL DEFAULT FALSE,
    QualityControlFlagForced BOOLEAN NOT NULL DEFAULT FALSE,
    QualityControlComments VARCHAR(512),

    CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
);

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);

/* Create study.SampleAvailabilityRule table */
CREATE TABLE study.SampleAvailabilityRule
(
    RowId SERIAL NOT NULL,
    Container EntityId NOT NULL,
    SortOrder INTEGER NOT NULL,
    RuleType VARCHAR(50),
    RuleData VARCHAR(250),
    MarkType VARCHAR(30),

    CONSTRAINT PL_SampleAvailabilityRule PRIMARY KEY (RowId)
);

ALTER TABLE exp.objectproperty DROP CONSTRAINT pk_objectproperty;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_object;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_propertydescriptor;
DROP INDEX exp.idx_objectproperty_propertyid;

DELETE FROM exp.ObjectProperty
WHERE propertyid IN (SELECT DP.propertyid FROM exp.propertydomain DP JOIN exp.domaindescriptor D on DP.domainid = D.domainid JOIN study.dataset DS ON D.domainuri = DS.typeuri);

ALTER TABLE exp.objectproperty
  ADD CONSTRAINT pk_objectproperty PRIMARY KEY (objectid, propertyid),
  ADD CONSTRAINT fk_objectproperty_object FOREIGN KEY (objectid)
      REFERENCES exp."object" (objectid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  ADD CONSTRAINT fk_objectproperty_propertydescriptor FOREIGN KEY (propertyid)
      REFERENCES exp.propertydescriptor (propertyid) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION;
CREATE INDEX idx_objectproperty_propertyid
  ON exp.objectproperty
  USING btree
  (propertyid);

CREATE TABLE study.VisitAliases
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NOT NULL,
    SequenceNum NUMERIC(15, 4) NOT NULL,

    CONSTRAINT PK_VisitNames PRIMARY KEY (RowId)
);

CREATE UNIQUE INDEX UQ_VisitAliases_Name ON study.VisitAliases (Container, LOWER(Name));

-- named sets of normalization factors
CREATE TABLE study.ParticipantCategory
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    Container ENTITYID NOT NULL,

    Label VARCHAR(200) NOT NULL,
    Type VARCHAR(60) NOT NULL,
    Shared boolean,
    AutoUpdate boolean,

	-- for queries
    QueryName VARCHAR(200),
    ViewName VARCHAR(200),
    SchemaName VARCHAR(50),

    -- for cohorts
    DatasetId Integer,
    GroupProperty VARCHAR(200)
);

-- Add Primary Key constraint
ALTER TABLE study.participantcategory
    ADD CONSTRAINT pk_participantcategory PRIMARY KEY (rowid);

-- Add Unique constraint
ALTER TABLE study.participantcategory
    ADD CONSTRAINT uq_label_container UNIQUE (label, container);

-- named sets of normalization factors
ALTER TABLE study.ParticipantCategory ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantCategory ADD Modified TIMESTAMP;

UPDATE study.ParticipantCategory SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantCategory SET Modified = Created;

-- Create an owner column to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD COLUMN OwnerId USERID NOT NULL DEFAULT -1;
UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE NOT shared;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,

	Label VARCHAR(200) NOT NULL,
    CategoryId Integer NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId)
);

-- Add Foreign Key constraint
ALTER TABLE study.participantgroup
    ADD CONSTRAINT fk_participantcategory_categoryid FOREIGN KEY (categoryid)
        REFERENCES study.participantcategory (rowid);

ALTER TABLE study.participantgroup
    ADD COLUMN filters text,
    ADD COLUMN description varchar(250);

ALTER TABLE study.ParticipantGroup ADD COLUMN CreatedBy USERID;
ALTER TABLE study.ParticipantGroup ADD COLUMN Created TIMESTAMP;
ALTER TABLE study.ParticipantGroup ADD COLUMN ModifiedBy USERID;
ALTER TABLE study.ParticipantGroup ADD COLUMN Modified TIMESTAMP;

UPDATE study.ParticipantGroup SET CreatedBy = ParticipantCategory.CreatedBy FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;
UPDATE study.ParticipantGroup SET Created = ParticipantCategory.Created FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;

UPDATE study.ParticipantGroup SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantGroup SET Modified = Created;

-- maps participants to participant groups
CREATE TABLE study.ParticipantGroupMap
(
    GroupId Integer NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT pk_participantGroupMap PRIMARY KEY (GroupId, ParticipantId, Container),
    CONSTRAINT fk_participantGroup_groupId FOREIGN KEY (GroupId) REFERENCES study.ParticipantGroup (RowId),
    CONSTRAINT fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant (Container, ParticipantId)
);

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container;

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	ON DELETE CASCADE;

-- History of all study snapshots (i.e., ancillary studies and published studies) and the settings used to generate
-- them. Rows are effectively owned by both the source and destination container; they remain as long as EITHER the
-- source or destination container exists. This table is used primarily to support nightly refresh of specimen data
-- (we need to save the protected settings, visit list, and participant list somewhere), but could easily support a
-- snapshot history feature.
CREATE TABLE study.StudySnapshot
(
    RowId SERIAL,
    Source ENTITYID NULL,       -- Source study container; null if this study has been deleted
    Destination ENTITYID NULL,  -- Destination study container; null if this study has been deleted
    CreatedBy USERID,
    Created TIMESTAMP,

    Refresh BOOLEAN NOT NULL,   -- Included in settings, but separate column allows quick filtering
    Settings TEXT,

    CONSTRAINT PK_StudySnapshot PRIMARY KEY (RowId)
);

CREATE INDEX IX_StudySnapshot_Source ON study.StudySnapshot(Source);
CREATE INDEX IX_StudySnapshot_Destination ON study.StudySnapshot(Destination, RowId);

ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified TIMESTAMP;

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;

ALTER TABLE study.StudySnapshot ADD COLUMN Type VARCHAR(10);

CREATE TABLE study.StudyDesignImmunogenTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignimmunogentypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignGenes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesigngenes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignRoutes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignroutes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSubTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsubtypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSampleTypes
(
  Name VARCHAR(200) NOT NULL,
  PrimaryType VARCHAR(200) NOT NULL,
  ShortSampleCode VARCHAR(2) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsampletypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignUnits
(
  Name VARCHAR(3) NOT NULL, -- storage name
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name)
);

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name TYPE VARCHAR(5);

CREATE TABLE study.StudyDesignAssays
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Description TEXT,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignassays PRIMARY KEY (Container, Name)
);

ALTER TABLE study.StudyDesignAssays ADD Target VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Methodology VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Category VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetFunction VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LeadContributor VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Contact VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Summary TEXT;
ALTER TABLE study.StudyDesignAssays ADD Keywords TEXT;

ALTER TABLE study.StudyDesignAssays ADD TargetType VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetSubtype VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Editorial TEXT;

ALTER TABLE study.StudyDesignAssays RENAME COLUMN Target TO Type;
ALTER TABLE study.StudyDesignAssays RENAME COLUMN Methodology TO Platform;

ALTER TABLE study.StudyDesignAssays ADD AlternateName VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab VARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI VARCHAR(200);

CREATE TABLE study.StudyDesignLabs
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignlabs PRIMARY KEY (Container, Name)
);

ALTER TABLE study.StudyDesignLabs ADD PI VARCHAR(200);
ALTER TABLE study.StudyDesignLabs ADD Description TEXT;
ALTER TABLE study.StudyDesignLabs ADD Summary TEXT;
ALTER TABLE study.StudyDesignLabs ADD Institution VARCHAR(200);

CREATE TABLE study.TreatmentVisitMap
(
  CohortId INT NOT NULL,
  TreatmentId INT NOT NULL,
  VisitId INT NOT NULL,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_CohortId_TreatmentId_VisitId PRIMARY KEY (CohortId, TreatmentId, VisitId, Container)
);

CREATE TABLE study.Objective
(
  RowId SERIAL,
  Label VARCHAR(200) NOT NULL,
  Type VARCHAR(200),
  Description TEXT,
  DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS',

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Objective PRIMARY KEY (RowId)
);

CREATE TABLE study.VisitTag
(
  Name VARCHAR(200) NOT NULL,
  Caption VARCHAR(200) NOT NULL,
  Description TEXT,
  SingleUse BOOLEAN NOT NULL DEFAULT false,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container)
);

ALTER TABLE study.VisitTag ADD Category VARCHAR(200);

-- new tables for storing the assay schedule information
CREATE TABLE study.AssaySpecimen
(
  RowId SERIAL NOT NULL,

  Container ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,

  AssayName VARCHAR(200),
  Description VARCHAR(200),
  LocationId INTEGER,
  Source VARCHAR(20),
  TubeType VARCHAR(64),
  PrimaryTypeId INTEGER,
  DerivativeTypeId INTEGER,

  CONSTRAINT PK_AssaySpecimen PRIMARY KEY (Container, RowId)
);

ALTER TABLE study.AssaySpecimen ADD COLUMN Lab VARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleType VARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleQuantity DOUBLE PRECISION;
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleUnits VARCHAR(5);
ALTER TABLE study.AssaySpecimen ADD COLUMN DataSet INTEGER;

CREATE TABLE study.AssaySpecimenVisit
(
  RowId SERIAL NOT NULL,

  Container ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,

  VisitId INTEGER,
  AssaySpecimenId INTEGER,

  CONSTRAINT PK_AssaySpecimenVisit PRIMARY KEY (Container, RowId)
);
CREATE UNIQUE INDEX UQ_VisitAssaySpecimen ON study.AssaySpecimenVisit(Container, VisitId, AssaySpecimenId);
CREATE UNIQUE INDEX UQ_AssaySpecimenVisit ON study.AssaySpecimenVisit(Container, AssaySpecimenId, VisitId);

CREATE TABLE study.VisitTagMap
(
  RowId     SERIAL NOT NULL,
  VisitTag  CHARACTER VARYING(200) NOT NULL,
  VisitId   INTEGER NOT NULL,
  CohortId  INTEGER,
  Container ENTITYID NOT NULL,
  CONSTRAINT PK_VisitTagMap PRIMARY KEY (Container, RowId),
  CONSTRAINT VisitTagMap_Container_VisitTag_Key UNIQUE (Container, VisitTag, VisitId, CohortId)
);

CREATE UNIQUE INDEX VisitTagMap_container_tag_visit_idx ON study.VisitTagMap (Container, VisitTag, VisitId) WHERE CohortId IS NULL;

CREATE TABLE study.DoseAndRoute
(
  RowId SERIAL,
  Label VARCHAR(600),
  Dose VARCHAR(200),
  Route VARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

ALTER TABLE study.DoseAndRoute DROP CONSTRAINT DoseAndRoute_Dose_Route_ProductId;
ALTER TABLE study.DoseAndRoute ADD CONSTRAINT DoseAndRoute_Container_Dose_Route_ProductId UNIQUE (Container, Dose, Route, ProductId);
ALTER TABLE study.DoseAndRoute DROP COLUMN Label;

CREATE TABLE study.StudyDesignChallengeTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignchallengetypes PRIMARY KEY (Container, Name)
);
