-- noinspection SqlResolveForFile

-- noinspection SqlResolveForFile @ object-type/"USERID"
-- noinspection SqlResolveForFile @ object-type/"ENTITYID"

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
GO
CREATE SCHEMA studyDataset;
GO
CREATE SCHEMA specimenTables;
GO
CREATE SCHEMA studydesign;
GO

CREATE TABLE study.Study
(
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    EntityId ENTITYID,
    TimepointType NVARCHAR(15) NOT NULL,
    StartDate DATETIME,
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty NVARCHAR(200) NULL,
    SecurityType NVARCHAR(32) NOT NULL,
    LSID NVARCHAR(200) NOT NULL,
    ManualCohortAssignment BIT NOT NULL DEFAULT 0,
    DefaultPipelineQCState INT,
    DefaultAssayQCState INT,
    DefaultDirectEntryQCState INT,
    ShowPrivateDataByDefault BIT NOT NULL DEFAULT 0,
    AllowReload BIT NOT NULL DEFAULT 0,
    ReloadInterval INT NULL,
    LastReload DATETIME NULL,
    ReloadUser UserId,
    AdvancedCohorts BIT NOT NULL DEFAULT 0,
    ParticipantCommentDataSetId INT NULL,
    ParticipantCommentProperty NVARCHAR(200) NULL,
    ParticipantVisitCommentDataSetId INT NULL,
    ParticipantVisitCommentProperty NVARCHAR(200) NULL,
    SubjectNounSingular NVARCHAR(50) NOT NULL DEFAULT 'Participant',
    SubjectNounPlural NVARCHAR(50) NOT NULL DEFAULT 'Participants',
    SubjectColumnName NVARCHAR(50) NOT NULL DEFAULT 'ParticipantId',

    CONSTRAINT PK_Study PRIMARY KEY (Container)
);

ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES core.DataStates (RowId);
ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES core.DataStates (RowId);
ALTER TABLE study.Study ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES core.DataStates (RowId);

ALTER TABLE study.Study ADD BlankQCStatePublic BIT NOT NULL DEFAULT 0
GO

ALTER TABLE study.Study ADD Description text
ALTER TABLE study.Study ADD ProtocolDocumentEntityId ENTITYID
ALTER TABLE study.Study ALTER COLUMN ProtocolDocumentEntityId ENTITYID NOT NULL
ALTER TABLE study.Study ADD SourceStudyContainerId ENTITYID
ALTER TABLE study.Study ADD DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS';
ALTER TABLE study.Study ADD investigator nvarchar(200)
ALTER TABLE study.Study ADD studyGrant nvarchar(200)

EXEC sp_RENAME 'study.Study.studyGrant', 'Grant', 'COLUMN';

ALTER TABLE study.study ADD DefaultTimepointDuration INT NOT NULL DEFAULT 1;

DELETE FROM study.study WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE study.Study
    ADD CONSTRAINT FK_Study_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;

ALTER TABLE study.Study ADD
    StudySnapshot INT NULL,
    LastSpecimenLoad DATETIME NULL;  -- Helps determine whether a specimen refresh is needed

CREATE INDEX IX_Study_StudySnapshot ON study.Study(StudySnapshot);

ALTER TABLE study.Study ADD AllowReqLocRepository BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocClinic BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocSAL BIT NOT NULL DEFAULT 1;
ALTER TABLE study.Study ADD AllowReqLocEndpoint BIT NOT NULL DEFAULT 1;
ALTER TABLE study.study ADD ParticipantAliasDatasetName NVARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasSourceColumnName NVARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasColumnName NVARCHAR(200);
ALTER TABLE study.study DROP COLUMN ParticipantAliasDatasetName;
ALTER TABLE study.study ADD ParticipantAliasDatasetId INT;

EXEC sp_rename 'study.study.ParticipantAliasSourceColumnName', 'ParticipantAliasSourceProperty', 'COLUMN';
EXEC sp_rename 'study.study.ParticipantAliasColumnName', 'ParticipantAliasProperty', 'COLUMN';

-- new fields to add to existing study properties table
ALTER TABLE study.Study ADD Species NVARCHAR(200);
ALTER TABLE study.Study ADD EndDate DATETIME;
ALTER TABLE study.Study ADD AssayPlan NTEXT;

ALTER TABLE study.Study ALTER COLUMN Description NVARCHAR(MAX);

ALTER TABLE study.Study ADD ShareDatasetDefinitions BIT NOT NULL DEFAULT 0;

-- Add new skip query validation column to study.study
ALTER TABLE study.Study ADD ValidateQueriesAfterImport BIT NOT NULL DEFAULT 0;
GO
UPDATE study.Study SET ValidateQueriesAfterImport = 1 WHERE AllowReload = 1;

ALTER TABLE study.Study ADD ShareVisitDefinitions BIT NOT NULL DEFAULT 0;

CREATE TABLE study.Cohort
(
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    LSID NVARCHAR(200) NOT NULL,

    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
);

ALTER TABLE study.Cohort ADD Enrolled BIT NOT NULL DEFAULT 1;

-- new fields to add to existing cohort table
ALTER TABLE study.Cohort ADD SubjectCount INT;
ALTER TABLE study.Cohort ADD Description NTEXT;

CREATE TABLE study.Visit
(
    RowId INT IDENTITY(1,1) NOT NULL,
    SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0,
    SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0,
    Label NVARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Container ENTITYID NOT NULL,
    VisitDateDatasetId INT,
    CohortId INT NULL,
    ChronologicalOrder INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT PK_Visit PRIMARY KEY (Container, RowId),
    CONSTRAINT UQ_Visit_ContSeqNum UNIQUE (Container, SequenceNumMin),
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Visit ADD SequenceNumHandling VARCHAR(32) NULL;
ALTER TABLE study.Visit ADD Description NTEXT;
GO

-- new fields to add to existing visit table, default SequenceNumTarget to SequenceNumMin
ALTER TABLE study.Visit ADD SequenceNumTarget NUMERIC(15,4) NOT NULL DEFAULT 0;

EXEC sp_rename 'study.visit.SequenceNumTarget', 'ProtocolDay', 'COLUMN';
GO

UPDATE study.Visit
SET ProtocolDay = Round((SequenceNumMax + SequenceNumMin)/2, 0)
FROM study.Study SS
WHERE SS.Container = study.Visit.Container AND SS.TimePointType = 'DATE';

--ALTER TABLE study.visit ALTER protocolday DROP NOT NULL;
EXEC core.fn_dropifexists 'Visit', 'study', 'DEFAULT', 'ProtocolDay';
GO
ALTER TABLE study.Visit ALTER COLUMN ProtocolDay NUMERIC(15,4) NULL;
GO
ALTER TABLE study.Visit ADD DEFAULT NULL FOR ProtocolDay;

CREATE TABLE study.VisitMap
(
    Container ENTITYID NOT NULL,
    VisitRowId INT NOT NULL DEFAULT -1,
    DataSetId INT NOT NULL,    -- FK
    Required BIT NOT NULL DEFAULT 1,

    CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId)
);

CREATE TABLE study.DataSet -- AKA CRF or Assay
(
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI NVARCHAR(200) NULL,
    Label NVARCHAR(200) NOT NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Category NVARCHAR(200) NULL,
    EntityId ENTITYID,
    VisitDatePropertyName NVARCHAR(200),
    KeyPropertyName NVARCHAR(50) NULL,         -- Property name in TypeURI
    Name VARCHAR(200) NOT NULL,
    Description NTEXT NULL,
    DemographicData BIT DEFAULT 0,
    CohortId INT NULL,
    ProtocolId INT NULL,
    KeyManagementType VARCHAR(10) NOT NULL,

    CONSTRAINT PK_DataSet PRIMARY KEY CLUSTERED (Container, DataSetId),
    CONSTRAINT UQ_DatasetName UNIQUE (Container, Name),
    CONSTRAINT UQ_DatasetLabel UNIQUE (Container, Label),
    CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

ALTER TABLE study.Dataset ADD CategoryId INT;
GO

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;
ALTER TABLE study.Dataset ADD Modified DATETIME;
ALTER TABLE study.Dataset ADD Type NVARCHAR(50) NOT NULL DEFAULT 'Standard';

-- clustered indexes just contribute to deadlocks, we don't really need this one

ALTER TABLE study.DataSet DROP CONSTRAINT PK_DataSet;
GO

ALTER TABLE study.DataSet ADD CONSTRAINT PK_DataSet PRIMARY KEY (Container, DataSetId);

-- Add new tag column to study.dataset
ALTER TABLE study.dataset ADD Tag VARCHAR(1000);

-- used by shared dataset definitions, specifies if data is shared across folders
--   NONE: (default) data is not shared across folders, same as any other container filtered table
--   ALL:  rows are all shared, visible in all study folders containing this dataset
--   PTID: rows are all shared, and are visible if PTID is a found in study.participants for the current folder
ALTER TABLE study.dataset ADD dataSharing NVARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE study.dataset ADD UseTimeKeyField BIT NOT NULL DEFAULT 0;
ALTER TABLE study.Dataset ALTER COLUMN TypeURI NVARCHAR(300);

-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
    Container ENTITYID NOT NULL,
    ParticipantId NVARCHAR(32) NOT NULL,
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,
    StartDate DATETIME,
    CurrentCohortId INT NULL,
    InitialCohortId INTEGER,

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);

-- TODO: These indexes are redundant... but the old create index, rename column, create index steps left us in this state
CREATE INDEX IX_Participant_CohortId ON study.Participant(CurrentCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD DateOffset INT NOT NULL DEFAULT ABS(CHECKSUM(NEWID())) % 364 + 1;

-- Random alternate IDs are set via code
ALTER TABLE study.Participant ADD AlternateId VARCHAR(32) NULL;

-- Track participant indexing in the participant table now
ALTER TABLE study.Participant ADD LastIndexed DATETIME NULL;

-- Add Modified column so we can actually use LastIndexed, #31139
ALTER TABLE study.Participant ADD Modified DATETIME;
GO

UPDATE study.Participant SET Modified = CURRENT_TIMESTAMP;
CREATE TABLE study.SampleRequestStatus
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    FinalState BIT NOT NULL DEFAULT 0,
    SpecimensLocked BIT NOT NULL DEFAULT 1,

    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE TABLE study.SampleRequestActor
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    PerSite Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE TABLE study.SampleRequest
(
    -- standard fields
    _ts TIMESTAMP,
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,

    StatusId INT NOT NULL,
    Comments NTEXT,
    DestinationSiteId INT NULL,
    Hidden Bit NOT NULL DEFAULT 0,
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
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description NVARCHAR(300),
    Complete Bit NOT NULL DEFAULT 0,
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
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    Comments NTEXT,
    EntryType NVARCHAR(64),
    RequirementId INT NULL,

    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId)
);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);
CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE TABLE study.SampleRequestSpecimen
(
    RowId INT IDENTITY(1, 1),
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenGlobalUniqueId NVARCHAR(100),
    Orphaned BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId)
);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);
CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);
CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE TABLE study.UploadLog
(
    RowId INT IDENTITY NOT NULL,
    Container ENTITYID NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy USERID NOT NULL,
    Description TEXT,
    FilePath NVARCHAR(400),   -- Stay under SQL Server's maximum key length of 900 bytes
    DatasetId INT NOT NULL,
    Status NVARCHAR(20),

    CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
);

CREATE TABLE study.ParticipantVisit
(
    Container ENTITYID NOT NULL,
    ParticipantId NVARCHAR(32) NOT NULL,
    VisitRowId INT NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME NULL,
    Day INTEGER,
    CohortID INT NULL,
    ParticipantSequenceKey NVARCHAR(200),

    CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId),
    CONSTRAINT FK_ParticipantVisit_Cohort FOREIGN KEY (CohortID) REFERENCES study.Cohort (RowId),
    CONSTRAINT UQ_StudyData_ParticipantSequenceKey UNIQUE (ParticipantSequenceKey, Container)
);

CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);
CREATE INDEX IX_ParticipantVisit_ParticipantSequenceKey ON study.ParticipantVisit(ParticipantSequenceKey, Container);

-- Rename 'ParticipantSequenceKey' to 'ParticipantSequenceNum' along with constraints and indices.
EXEC sp_rename 'study.ParticipantVisit.ParticipantSequenceKey', 'ParticipantSequenceNum', 'COLUMN';
EXEC sp_rename 'study.ParticipantVisit.UQ_StudyData_ParticipantSequenceKey', 'UQ_ParticipantVisit_ParticipantSequenceNum';
EXEC sp_rename 'study.ParticipantVisit.IX_ParticipantVisit_ParticipantSequenceKey', 'IX_ParticipantVisit_ParticipantSequenceNum', 'INDEX';
GO

-- To change the PK, it is more efficient to drop all other indexes (including unique constraints),
-- drop and recreate PK, and then rebuild indexes

ALTER TABLE study.ParticipantVisit DROP CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum;

-- changing order of keys to make supporting index useful for Container+Participant queries
ALTER TABLE study.ParticipantVisit DROP CONSTRAINT PK_ParticipantVisit;

-- Consider:  do we need a unique constraint on ParticipantSequenceNum if we have separate ones on Participant, SequenceNum ??
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_Container;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_ParticipantId;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_ParticipantSequenceNum;
DROP INDEX study.ParticipantVisit.IX_ParticipantVisit_SequenceNum;

-- Was previously Container, SequenceNum, ParticipantId
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY CLUSTERED
  (Container, ParticipantId, SequenceNum);


ALTER TABLE study.ParticipantVisit ADD CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum UNIQUE
(ParticipantSequenceNum ASC, Container ASC);

CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit (ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit (SequenceNum);

-- clean up some bad participantsequencenum values seen in the wild
DELETE FROM Study.ParticipantVisit WHERE ParticipantSequenceNum = 'NULL';

UPDATE study.participantvisit SET visitrowid=-1 WHERE visitrowid IS NULL;
ALTER TABLE study.participantvisit ALTER COLUMN visitrowid INT NOT NULL;
ALTER TABLE study.participantvisit ADD CONSTRAINT study_pv_visitrowid_def DEFAULT -1 for visitrowid;

EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_ParticipantId';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_ParticipantVisit_SequenceNum';

CREATE INDEX IX_PV_SequenceNum ON study.ParticipantVisit (Container, SequenceNum) INCLUDE (VisitRowId);

EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'IX_PV_SequenceNum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_sequencenum';
EXEC core.fn_dropifexists 'ParticipantVisit', 'study', 'INDEX', 'ix_participantvisit_visitrowid';

-- For Resync perf
CREATE INDEX ix_participantvisit_sequencenum ON study.participantvisit (container, participantid, sequencenum, ParticipantSequenceNum);

-- Adding as an explicit index because it got lost on postgresql as an include column
CREATE INDEX ix_participantvisit_visitrowid ON study.participantvisit (visitrowid);

CREATE TABLE study.StudyDesign
(
    -- standard fields
    _ts TIMESTAMP,
    StudyId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    PublicRevision INT NULL,
    DraftRevision INT NULL,
    Label NVARCHAR(200) NOT NULL,
    Active BIT DEFAULT 0,
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
    RowId INT IDENTITY(1,1),
    StudyId INT NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    Container ENTITYID NOT NULL,
    Revision INT NOT NULL,
    Draft Bit NOT NULL DEFAULT 1,
    Label NVARCHAR(200) NOT NULL,
    Description NTEXT,
    XML NTEXT,

    CONSTRAINT PK_StudyDesignVersion PRIMARY KEY (StudyId,Revision),
    CONSTRAINT FK_StudyDesignVersion_StudyDesign FOREIGN KEY (StudyId) REFERENCES study.StudyDesign(StudyId),
    CONSTRAINT UQ_StudyDesignVersion UNIQUE (Container,Label,Revision)
);

CREATE TABLE study.ParticipantView
(
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Container ENTITYID NOT NULL,
    Body TEXT,
    Active BIT NOT NULL,

    CONSTRAINT PK_ParticipantView PRIMARY KEY (RowId),
    CONSTRAINT FK_ParticipantView_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);

CREATE TABLE study.SpecimenComment
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    GlobalUniqueId NVARCHAR(50) NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Comment NTEXT,
    SpecimenHash NVARCHAR(256),
    QualityControlFlag BIT NOT NULL DEFAULT 0,
    QualityControlFlagForced BIT NOT NULL DEFAULT 0,
    QualityControlComments NVARCHAR(512),

    CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
);

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);

CREATE TABLE study.SampleAvailabilityRule
(
    RowId INT IDENTITY(1,1),
    Container EntityId NOT NULL,
    SortOrder INTEGER NOT NULL,
    RuleType NVARCHAR(50),
    RuleData NVARCHAR(250),
    MarkType NVARCHAR(30),

    CONSTRAINT PL_SampleAvailabilityRule PRIMARY KEY (RowId)
);

CREATE TABLE study.VisitAliases
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    SequenceNum NUMERIC(15, 4) NOT NULL,

    CONSTRAINT PK_VisitNames PRIMARY KEY (RowId)
);

CREATE UNIQUE INDEX UQ_VisitAliases_Name ON study.VisitAliases (Container, Name);

-- named sets of normalization factors
CREATE TABLE study.ParticipantCategory
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    Container ENTITYID NOT NULL,

    Label NVARCHAR(200) NOT NULL,
    Type NVARCHAR(60) NOT NULL,
    Shared BIT,
    AutoUpdate BIT,

	  -- for queries
    QueryName NVARCHAR(200),
    ViewName NVARCHAR(200),
    SchemaName NVARCHAR(50),

    -- for cohorts
    DatasetId INT,
    GroupProperty NVARCHAR(200),

    CONSTRAINT pk_participantCategory PRIMARY KEY (RowId),
    CONSTRAINT uq_Label_Container UNIQUE (Label, Container)
)
GO

-- named sets of normalization factors
ALTER TABLE study.ParticipantCategory ADD ModifiedBy USERID
GO
ALTER TABLE study.ParticipantCategory ADD Modified DATETIME
GO
UPDATE study.ParticipantCategory SET ModifiedBy = CreatedBy
GO
UPDATE study.ParticipantCategory SET Modified = Created
GO

-- Create an owner column to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD OwnerId USERID NOT NULL DEFAULT -1;
GO

UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE Shared <> 1;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN Shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);
GO

-- represents a grouping category for a participant category
CREATE TABLE study.ParticipantGroup
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,

    Label NVARCHAR(200) NOT NULL,
    CategoryId INT NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId)
)
GO

-- Add Foreign Key constraint
ALTER TABLE study.ParticipantGroup
    ADD CONSTRAINT fk_participantCategory_categoryId FOREIGN KEY (CategoryId) REFERENCES study.ParticipantCategory (RowId)
GO

ALTER TABLE study.ParticipantGroup ADD
    Filter NTEXT NULL,
    Description NVARCHAR(250) NULL
GO

EXEC sp_RENAME 'study.ParticipantGroup.Filter', 'Filters', 'COLUMN';

ALTER TABLE study.ParticipantGroup ADD CreatedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Created DATETIME;
ALTER TABLE study.ParticipantGroup ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Modified DATETIME;
GO

UPDATE study.ParticipantGroup SET CreatedBy = ParticipantCategory.CreatedBy FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;
UPDATE study.ParticipantGroup SET Created = ParticipantCategory.Created FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;

UPDATE study.ParticipantGroup SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantGroup SET Modified = Created;

-- maps participants to participant groups
CREATE TABLE study.ParticipantGroupMap
(
    GroupId INT NOT NULL,
    ParticipantId NVARCHAR(32) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT pk_participantGroupMap PRIMARY KEY (GroupId, ParticipantId, Container),
    CONSTRAINT fk_participantGroup_groupId FOREIGN KEY (GroupId) REFERENCES study.ParticipantGroup (RowId),
    CONSTRAINT fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
)
GO

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container
GO

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	  fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	  ON DELETE CASCADE
GO

-- History of all study snapshots (i.e., ancillary studies and published studies) and the settings used to generate
-- them. Rows are effectively owned by both the source and destination container; they remain as long as EITHER the
-- source or destination container exists. This table is used primarily to support nightly refresh of specimen data
-- (we need to save the protected settings, visit list, and participant list somewhere), but could easily support a
-- snapshot history feature.
CREATE TABLE study.StudySnapshot
(
    RowId INT IDENTITY(1,1),
    Source ENTITYID NULL,       -- Source study container; null if this study has been deleted
    Destination ENTITYID NULL,  -- Destination study container; null if this study has been deleted
    CreatedBy USERID,
    Created DATETIME,

    Refresh BIT NOT NULL,       -- Included in settings, but separate column allows quick filtering
    Settings TEXT,

    CONSTRAINT PK_StudySnapshot PRIMARY KEY (RowId)
);

CREATE INDEX IX_StudySnapshot_Source ON study.StudySnapshot(Source);
CREATE INDEX IX_StudySnapshot_Destination ON study.StudySnapshot(Destination, RowId);

ALTER TABLE study.StudySnapshot ADD ModifiedBy USERID;
ALTER TABLE study.StudySnapshot ADD Modified DATETIME;
GO

UPDATE study.StudySnapshot SET ModifiedBy = CreatedBy;
UPDATE study.StudySnapshot SET Modified = Created;
GO

ALTER TABLE study.StudySnapshot ADD Type VARCHAR(10);
GO

CREATE TABLE study.StudyDesignImmunogenTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignimmunogentypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignGenes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesigngenes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignRoutes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignroutes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSubTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsubtypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSampleTypes
(
  Name NVARCHAR(200) NOT NULL,
  PrimaryType NVARCHAR(200) NOT NULL,
  ShortSampleCode NVARCHAR(2) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsampletypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignUnits
(
  Name NVARCHAR(3) NOT NULL, -- storage name
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name)
);

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits DROP CONSTRAINT pk_studydesignunits;
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name NVARCHAR(5) NOT NULL;
ALTER TABLE study.StudyDesignUnits ADD CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name);

CREATE TABLE study.StudyDesignAssays
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Description TEXT,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignassays PRIMARY KEY (Container, Name)
);

ALTER TABLE study.StudyDesignAssays ADD Target NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Methodology NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Category NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetFunction NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LeadContributor NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Contact NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Summary TEXT;
ALTER TABLE study.StudyDesignAssays ADD Keywords TEXT;
ALTER TABLE study.StudyDesignAssays ADD TargetType NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD TargetSubtype NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Editorial NVARCHAR(MAX);

EXEC sp_rename 'study.StudyDesignAssays.Target', 'Type', 'COLUMN';
GO
EXEC sp_rename 'study.StudyDesignAssays.Methodology', 'Platform', 'COLUMN';
GO

ALTER TABLE study.StudyDesignAssays ADD AlternateName NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD Lab NVARCHAR(200);
ALTER TABLE study.StudyDesignAssays ADD LabPI NVARCHAR(200);

CREATE TABLE study.StudyDesignLabs
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignlabs PRIMARY KEY (Container, Name)
);

ALTER TABLE study.StudyDesignLabs ADD PI NVARCHAR(200);
ALTER TABLE study.StudyDesignLabs ADD Description TEXT;
ALTER TABLE study.StudyDesignLabs ADD Summary TEXT;
ALTER TABLE study.StudyDesignLabs ADD Institution NVARCHAR(200);

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
  RowId INT IDENTITY(1, 1) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Type NVARCHAR(200),
  Description NTEXT,
  DescriptionRendererType NVARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS',

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Objective PRIMARY KEY (RowId)
);

CREATE TABLE study.VisitTag
(
  Name NVARCHAR(200) NOT NULL,
  Caption NVARCHAR(200) NOT NULL,
  Description NVARCHAR(MAX),
  SingleUse BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Name_Container PRIMARY KEY (Name, Container)
);

ALTER TABLE study.VisitTag ADD Category NVARCHAR(200);

-- new tables for storing the assay schedule information
CREATE TABLE study.AssaySpecimen
(
  RowId INT IDENTITY(1, 1) NOT NULL,

  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  AssayName NVARCHAR(200),
  Description NVARCHAR(200),
  LocationId INTEGER,
  Source NVARCHAR(20),
  TubeType NVARCHAR(64),
  PrimaryTypeId INTEGER,
  DerivativeTypeId INTEGER,

  CONSTRAINT PK_AssaySpecimen PRIMARY KEY (Container, RowId)
);

ALTER TABLE study.AssaySpecimen ADD Lab NVARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD SampleType NVARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD SampleQuantity DOUBLE PRECISION;
ALTER TABLE study.AssaySpecimen ADD SampleUnits NVARCHAR(5);
ALTER TABLE study.AssaySpecimen ADD DataSet INTEGER;

CREATE TABLE study.AssaySpecimenVisit
(
  RowId INT IDENTITY(1, 1) NOT NULL,

  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  VisitId INTEGER,
  AssaySpecimenId INTEGER,

  CONSTRAINT PK_AssaySpecimenVisit PRIMARY KEY (Container, RowId)
);
CREATE UNIQUE INDEX UQ_VisitAssaySpecimen ON study.AssaySpecimenVisit(Container, VisitId, AssaySpecimenId);
CREATE UNIQUE INDEX UQ_AssaySpecimenVisit ON study.AssaySpecimenVisit(Container, AssaySpecimenId, VisitId);

CREATE TABLE study.VisitTagMap
(
  RowId     INT IDENTITY(1,1),
  VisitTag  NVARCHAR(200) NOT NULL,
  VisitId   INTEGER NOT NULL,
  CohortId  INTEGER,
  Container ENTITYID NOT NULL,
  CONSTRAINT PK_VisitTagMap PRIMARY KEY (Container, RowId),
  CONSTRAINT VisitTagMap_Container_VisitTag_Key UNIQUE (Container, VisitTag, VisitId, CohortId)
);

CREATE TABLE study.DoseAndRoute
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Label NVARCHAR(600),
  Dose NVARCHAR(200),
  Route NVARCHAR(200),
  ProductId INT NOT NULL,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_DoseAndRoute PRIMARY KEY (RowId),
  CONSTRAINT DoseAndRoute_Dose_Route_ProductId UNIQUE (Dose, Route, ProductId)
);

ALTER TABLE study.DoseAndRoute DROP CONSTRAINT DoseAndRoute_Dose_Route_ProductId;
ALTER TABLE study.DoseAndRoute ADD CONSTRAINT DoseAndRoute_Container_Dose_Route_ProductId UNIQUE (Container, Dose, Route, ProductId);

ALTER TABLE study.DoseAndRoute DROP COLUMN Label;

CREATE TABLE study.StudyDesignChallengeTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignchallengetypes PRIMARY KEY (Container, Name)
);
