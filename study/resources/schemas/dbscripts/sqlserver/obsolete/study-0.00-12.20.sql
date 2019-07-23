/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

/* study-0.00-10.20.sql */

CREATE SCHEMA study;
GO
CREATE SCHEMA studyDataset;
GO
CREATE SCHEMA assayresult;
GO

CREATE TABLE study.QCState
(
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(64) NULL,
    Description NVARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BIT NOT NULL,

    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);

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

    CONSTRAINT PK_Study PRIMARY KEY (Container),
    CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId)
);

CREATE TABLE study.Site
(
    EntityId ENTITYID NOT NULL DEFAULT NEWID(),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    RowId INT IDENTITY(1,1),
    ExternalId INT,
    LdmsLabCode INT,
    LabwareLabCode NVARCHAR(20),
    LabUploadCode NVARCHAR(10),
    Repository BIT,
    Clinic BIT,
    SAL BIT,
    Endpoint BIT,

    CONSTRAINT PK_Site PRIMARY KEY (RowId)
);

CREATE TABLE study.Cohort
(
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    LSID NVARCHAR(200) NOT NULL,

    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
);

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

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId),
    CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
    CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId),
);

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId)
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);

-- TODO: These indexes are redundant... but the old create index, rename column, create index steps left us in this state
CREATE INDEX IX_Participant_CohortId ON study.Participant(CurrentCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

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
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
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
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId),
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (SiteId) REFERENCES study.Site(RowId)
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
    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);
CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE TABLE study.Report
(
    ReportId INT IDENTITY NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ContainerId ENTITYID NOT NULL,
    Label NVARCHAR(100) NOT NULL,
    Params NVARCHAR(512) NULL,
    ReportType NVARCHAR(100) NOT NULL,
    Scope INT NOT NULL,
    ShowWithDataset INT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
);

-- TODO: Check LDMS/Labware code lengths (additive vs. derivative vs. primary)

CREATE TABLE study.SpecimenAdditive
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    LdmsAdditiveCode NVARCHAR(30),
    LabwareAdditiveCode NVARCHAR(20),
    Additive NVARCHAR(100),

    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additive UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);
CREATE INDEX IX_SpecimenAdditive_ExternalId ON study.SpecimenAdditive(ExternalId);
CREATE INDEX IX_SpecimenAdditive_Additive ON study.SpecimenAdditive(Additive);

CREATE TABLE study.SpecimenDerivative
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    LdmsDerivativeCode NVARCHAR(20),
    LabwareDerivativeCode NVARCHAR(20),
    Derivative NVARCHAR(100),

    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivative UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);
CREATE INDEX IX_SpecimenDerivative_ExternalId ON study.SpecimenDerivative(ExternalId);
CREATE INDEX IX_SpecimenDerivative_Derivative ON study.SpecimenDerivative(Derivative);

CREATE TABLE study.SpecimenPrimaryType
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    PrimaryTypeLdmsCode NVARCHAR(5),
    PrimaryTypeLabwareCode NVARCHAR(5),
    PrimaryType NVARCHAR(100),

    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryType UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);
CREATE INDEX IX_SpecimenPrimaryType_ExternalId ON study.SpecimenPrimaryType(ExternalId);
CREATE INDEX IX_SpecimenPrimaryType_PrimaryType ON study.SpecimenPrimaryType(PrimaryType);

-- Create the specimen table, which will hold static properties of a specimen draw (versus a vial)
CREATE TABLE study.Specimen
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SpecimenHash NVARCHAR(256),
    Ptid NVARCHAR(32),
    VisitDescription NVARCHAR(10),
    VisitValue NUMERIC(15,4),
    VolumeUnits NVARCHAR(20),
    PrimaryTypeId INTEGER,
    AdditiveTypeId INTEGER,
    DerivativeTypeId INTEGER,
    DerivativeTypeId2 INTEGER,
    SubAdditiveDerivative NVARCHAR(50),
    DrawTimestamp DATETIME,
    SalReceiptDate DATETIME,
    ClassId NVARCHAR(20),
    ProtocolNumber NVARCHAR(20),
    OriginatingLocationId INTEGER,
    TotalVolume FLOAT,
    AvailableVolume FLOAT,
    VialCount INTEGER,
    LockedInRequestCount INTEGER,
    AtRepositoryCount INTEGER,
    AvailableCount INTEGER,
    ExpectedAvailableCount INTEGER,
    ParticipantSequenceKey NVARCHAR(200),
    ProcessingLocation INT,
    FirstProcessedByInitials NVARCHAR(32),

    CONSTRAINT PK_Specimen PRIMARY KEY (RowId)
);

CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);
CREATE INDEX IX_Specimen_Container ON study.Specimen(Container);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);
CREATE INDEX IX_Specimen_Container_SpecimenHash ON study.Specimen(Container, SpecimenHash);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Specimen_DerivativeTypeId2 ON study.Specimen(DerivativeTypeId2);
CREATE INDEX IX_Specimen_ParticipantSequenceKey ON study.Specimen(ParticipantSequenceKey, Container);

/*
LDMS Name   Export Name             Association
dervst2     derivative_type_id_2    the vial
froztm      frozen_time             the vial
proctm      processing_time         the vial
frlab       shipped_from_lab        single vial location
tolab       shipped_to_lab          single vial location
privol      primary_volume          the draw
pvlunt      primary_volume_units    the draw
*/

CREATE TABLE study.Vial
(
    RowId INT NOT NULL, -- FK exp.Material
    Container ENTITYID NOT NULL,
    GlobalUniqueId NVARCHAR(50) NOT NULL,
    Volume FLOAT,
    Requestable BIT,
    CurrentLocation INT,
    SpecimenHash NVARCHAR(256),
    AtRepository BIT NOT NULL DEFAULT 0,
    LockedInRequest BIT NOT NULL DEFAULT 0,
    Available BIT NOT NULL DEFAULT 0,
    ProcessingLocation INT,
    SpecimenId INTEGER NOT NULL,
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20),
    FirstProcessedByInitials NVARCHAR(32),
    AvailabilityReason NVARCHAR(256),

    CONSTRAINT PK_Specimens PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_GlobalId UNIQUE (GlobalUniqueId, Container),
    CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId),
    CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
);

CREATE INDEX IX_Vial_Container ON study.Vial(Container);
CREATE INDEX IX_Vial_GlobalUniqueId ON study.Vial(GlobalUniqueId);
CREATE INDEX IX_Vial_CurrentLocation ON study.Vial(CurrentLocation);
CREATE INDEX IX_Vial_Container_SpecimenHash ON study.Vial(Container, SpecimenHash);
CREATE INDEX IX_Vial_SpecimenId ON study.Vial(SpecimenId);

CREATE TABLE study.SpecimenEvent
(
    RowId INT IDENTITY(1, 1),
    Container ENTITYID NOT NULL,
    ExternalId INT,
    VialId INT NOT NULL,
    LabId INT,
    UniqueSpecimenId NVARCHAR(50),
    ParentSpecimenId INT,
    Stored INT,
    StorageFlag INT,
    StorageDate DATETIME,
    ShipFlag INT,
    ShipBatchNumber INT,
    ShipDate DATETIME,
    ImportedBatchNumber INT,
    LabReceiptDate DATETIME,
    Comments NVARCHAR(200),
    SpecimenCondition NVARCHAR(30),
    SampleNumber INT,
    XSampleOrigin NVARCHAR(50),
    ExternalLocation NVARCHAR(50),
    UpdateTimestamp DATETIME,
    OtherSpecimenId NVARCHAR(50),
    ExpectedTimeUnit NVARCHAR(15),
    ExpectedTimeValue FLOAT,
    GroupProtocol INT,
    RecordSource NVARCHAR(20),
    freezer NVARCHAR(200),
    fr_level1 NVARCHAR(200),
    fr_level2 NVARCHAR(200),
    fr_container NVARCHAR(200),
    fr_position NVARCHAR(200),
    SpecimenNumber NVARCHAR(50),
    ShippedFromLab NVARCHAR(32),
    ShippedToLab NVARCHAR(32),
    Ptid NVARCHAR(32),
    DrawTimestamp DATETIME,
    SalReceiptDate DATETIME,
    ClassId NVARCHAR(20),
    VisitValue NUMERIC(15,4),
    ProtocolNumber NVARCHAR(20),
    VisitDescription NVARCHAR(10),
    Volume double precision,
    VolumeUnits NVARCHAR(20),
    SubAdditiveDerivative NVARCHAR(50),
    PrimaryTypeId INT,
    DerivativeTypeId INT,
    AdditiveTypeId INT,
    DerivativeTypeId2 INT,
    OriginatingLocationId INT,
    FrozenTime DATETIME,
    ProcessingTime DATETIME,
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20),
    ProcessedByInitials NVARCHAR(32),
    ProcessingDate DATETIME,

    CONSTRAINT PK_SpecimensEvents PRIMARY KEY (RowId),
    CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (VialId) REFERENCES study.Vial(RowId),
    CONSTRAINT FK_Specimens_Site FOREIGN KEY (LabId) REFERENCES study.Site(RowId)
);

-- TODO: Name mismatch (SpecimenId vs. VialId)
CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(VialId);
CREATE INDEX IX_SpecimenEvent_Container ON study.SpecimenEvent(Container);
CREATE INDEX IX_SpecimenEvent_LabId ON study.SpecimenEvent(LabId);

CREATE TABLE study.SampleRequestSpecimen
(
    RowId INT IDENTITY(1, 1),
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenGlobalUniqueId NVARCHAR(100),
    Orphaned BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
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
    Type NVARCHAR(200),

    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

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
);

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);
CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);

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
);

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);
CREATE INDEX IX_Well_Container ON study.Well(Container);

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

/* study-10.20-10.30.sql */

ALTER TABLE study.Study ADD BlankQCStatePublic BIT NOT NULL DEFAULT 0
GO

/* study-10.30-11.10.sql */

ALTER TABLE study.SpecimenEvent ADD TotalCellCount FLOAT
GO

ALTER TABLE study.Vial ADD TotalCellCount FLOAT
GO

/* study-11.10-11.20.sql */

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
CREATE TABLE study.ParticipantClassifications
(
    RowId INT IDENTITY(1,1) NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
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

    CONSTRAINT pk_participantClassifications PRIMARY KEY (RowId)
)
GO

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,

	  Label NVARCHAR(200) NOT NULL,
    ClassificationId INT NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId),
    CONSTRAINT fk_participantClassifications_classificationId FOREIGN KEY (ClassificationId) REFERENCES study.ParticipantClassifications (RowId)
)
GO

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

ALTER TABLE study.ParticipantClassifications DROP COLUMN Created
GO
ALTER TABLE study.ParticipantClassifications ADD Created DATETIME
GO

ALTER TABLE study.ParticipantGroupMap DROP CONSTRAINT fk_participant_participantId_container
GO

ALTER TABLE study.ParticipantGroupMap ADD CONSTRAINT
	  fk_participant_participantId_container FOREIGN KEY (Container, ParticipantId) REFERENCES study.Participant(Container, ParticipantId)
	  ON DELETE CASCADE
GO

ALTER TABLE study.SpecimenEvent ADD TubeType NVARCHAR(32)
GO

ALTER TABLE study.Vial ADD TubeType NVARCHAR(32)
GO

-- Rename from ParticipantClassifications to ParticipantCategory (Singular)
EXEC sp_rename 'study.ParticipantClassifications', 'ParticipantCategory'
GO

-- Drop Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	  DROP CONSTRAINT fk_participantClassifications_classificationId
GO

-- Drop Primary Key constraint
ALTER TABLE study.ParticipantCategory
	  DROP CONSTRAINT pk_participantClassifications
GO

-- Add Primary Key constraint
ALTER TABLE study.ParticipantCategory
	  ADD CONSTRAINT pk_participantCategory PRIMARY KEY (RowId)
GO

-- Rename foreign key column
EXEC sp_rename 'study.ParticipantGroup.ClassificationId', 'CategoryId', 'COLUMN'
GO

-- Add Foreign Key constraint
ALTER TABLE study.ParticipantGroup
	ADD CONSTRAINT fk_participantCategory_categoryId FOREIGN KEY (CategoryId) REFERENCES study.ParticipantCategory (RowId)
GO

-- Add Unique constraint
ALTER TABLE study.ParticipantCategory
	ADD CONSTRAINT uq_Label_Container UNIQUE (Label, Container)
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

/* study-11.20-11.30.sql */

ALTER TABLE study.Study ADD Description text
GO

ALTER TABLE study.Study ADD ProtocolDocumentEntityId ENTITYID
GO

ALTER TABLE study.Study ALTER COLUMN ProtocolDocumentEntityId ENTITYID NOT NULL
GO

-- populate the view category table with the dataset categories (need the special conditional to work around a mid-script failure that
-- was checked in earlier

IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'study' AND table_name = 'DataSet' AND column_name = 'CategoryId')
BEGIN
  INSERT INTO core.ViewCategory (Container, Label, CreatedBy, ModifiedBy)
    SELECT Container, Category, 0, 0 FROM study.Dataset WHERE LEN(Category) > 0 GROUP BY Container, Category;

  ALTER TABLE study.Dataset ADD CategoryId INT;
END
GO

UPDATE study.Dataset
    SET CategoryId = (SELECT rowId FROM core.ViewCategory vc WHERE Dataset.container = vc.container AND Dataset.category = vc.label);

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;

ALTER TABLE study.Study ADD SourceStudyContainerId ENTITYID
GO

ALTER TABLE study.Study ADD DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS';

ALTER TABLE study.Study ADD investigator nvarchar(200)
GO

ALTER TABLE study.Study ADD studyGrant nvarchar(200)
GO

EXEC sp_RENAME 'study.Study.studyGrant', 'Grant', 'COLUMN';

/* study-11.30-12.10.sql */

ALTER TABLE study.Dataset ADD Modified DATETIME;
ALTER TABLE study.Dataset ADD Type NVARCHAR(50) NOT NULL DEFAULT 'Standard';

/* study-12.10-12.20.sql */

-- Rename 'ParticipantSequenceKey' to 'ParticipantSequenceNum' along with constraints and indices.
EXEC sp_rename 'study.ParticipantVisit.ParticipantSequenceKey', 'ParticipantSequenceNum', 'COLUMN';
EXEC sp_rename 'study.ParticipantVisit.UQ_StudyData_ParticipantSequenceKey', 'UQ_ParticipantVisit_ParticipantSequenceNum';
EXEC sp_rename 'study.ParticipantVisit.IX_ParticipantVisit_ParticipantSequenceKey', 'IX_ParticipantVisit_ParticipantSequenceNum', 'INDEX';
GO


EXEC sp_rename 'study.Specimen.ParticipantSequenceKey', 'ParticipantSequenceNum', 'COLUMN';
EXEC sp_rename 'study.Specimen.IX_Specimen_ParticipantSequenceKey', 'IX_Specimen_ParticipantSequenceNum', 'INDEX';
GO


ALTER TABLE study.ParticipantGroup ADD
    Filter NTEXT NULL,
    Description NVARCHAR(250) NULL
GO

EXEC sp_RENAME 'study.ParticipantGroup.Filter', 'Filters', 'COLUMN';

ALTER TABLE study.study ADD DefaultTimepointDuration INT NOT NULL DEFAULT 1;

DELETE FROM study.study WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE study.Study
    ADD CONSTRAINT FK_Study_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

UPDATE study.StudyDesign SET sourceContainer=Container WHERE sourceContainer NOT IN (SELECT entityid FROM core.containers);

ALTER TABLE study.ParticipantGroup ADD CreatedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Created DATETIME;
ALTER TABLE study.ParticipantGroup ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantGroup ADD Modified DATETIME;
GO

UPDATE study.ParticipantGroup SET CreatedBy = ParticipantCategory.CreatedBy FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;
UPDATE study.ParticipantGroup SET Created = ParticipantCategory.Created FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;

UPDATE study.ParticipantGroup SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantGroup SET Modified = Created;
