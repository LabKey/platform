/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
CREATE SCHEMA studyDataset;
CREATE SCHEMA assayresult;

CREATE TABLE study.QCState
(
    RowId SERIAL,
    Label VARCHAR(64) NULL,
    Description VARCHAR(500) NULL,
    Container ENTITYID NOT NULL,
    PublicData BOOLEAN NOT NULL,
    CONSTRAINT PK_QCState PRIMARY KEY (RowId),
    CONSTRAINT UQ_QCState_Label UNIQUE(Label, Container)
);

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

    CONSTRAINT PK_Study PRIMARY KEY (Container),
    CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId)
);

CREATE TABLE study.Site
(
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    ExternalId INT,
    LdmsLabCode INT,
    LabwareLabCode VARCHAR(20),
    LabUploadCode VARCHAR(10),
    Repository BOOLEAN,
    Clinic BOOLEAN,
    SAL BOOLEAN,
    Endpoint BOOLEAN,

    CONSTRAINT PK_Site PRIMARY KEY (RowId)
);

CREATE TABLE study.Cohort
(
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    LSID VARCHAR(200) NOT NULL,

    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
);

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
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
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

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId),
    CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
    CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
);

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);

-- TODO: These indexes are redundant... but the old create index, rename column, create index steps left us in this state
CREATE INDEX IX_Participant_CohortId ON study.Participant(CurrentCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

CREATE TABLE study.Report
(
    ReportId SERIAL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ContainerId ENTITYID NOT NULL,
    Label VARCHAR(100) NOT NULL,
    Params VARCHAR(512) NULL,
    ReportType VARCHAR(100) NOT NULL,
    Scope INT NOT NULL,
    ShowWithDataset INT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
);

-- TODO: Check LDMS/Labware code lengths (additive vs. derivative vs. primary)

CREATE TABLE study.SpecimenAdditive
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    LdmsAdditiveCode VARCHAR(30),
    LabwareAdditiveCode VARCHAR(20),
    Additive VARCHAR(100),

    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additive UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);
CREATE INDEX IX_SpecimenAdditive_ExternalId ON study.SpecimenAdditive(ExternalId);
CREATE INDEX IX_SpecimenAdditive_Additive ON study.SpecimenAdditive(Additive);

CREATE TABLE study.SpecimenDerivative
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    LdmsDerivativeCode VARCHAR(30),
    LabwareDerivativeCode VARCHAR(20),
    Derivative VARCHAR(100),

    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivative UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);
CREATE INDEX IX_SpecimenDerivative_ExternalId ON study.SpecimenDerivative(ExternalId);
CREATE INDEX IX_SpecimenDerivative_Derivative ON study.SpecimenDerivative(Derivative);

CREATE TABLE study.SpecimenPrimaryType
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ExternalId INT NOT NULL DEFAULT 0,
    PrimaryTypeLdmsCode VARCHAR(5),
    PrimaryTypeLabwareCode VARCHAR(5),
    PrimaryType VARCHAR(100),

    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryType UNIQUE (ExternalId, Container)
);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);
CREATE INDEX IX_SpecimenPrimaryType_ExternalId ON study.SpecimenPrimaryType(ExternalId);
CREATE INDEX IX_SpecimenPrimaryType_PrimaryType ON study.SpecimenPrimaryType(PrimaryType);

-- Create specimen table, which will hold static properties of a specimen draw (versus a vial)
CREATE TABLE study.Specimen
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    SpecimenHash VARCHAR(256),
    Ptid VARCHAR(32),
    VisitDescription VARCHAR(10),
    VisitValue NUMERIC(15,4),
    VolumeUnits VARCHAR(20),
    PrimaryTypeId INTEGER,
    AdditiveTypeId INTEGER,
    DerivativeTypeId INTEGER,
    DerivativeTypeId2 INTEGER,
    SubAdditiveDerivative VARCHAR(50),
    DrawTimestamp TIMESTAMP,
    SalReceiptDate TIMESTAMP,
    ClassId VARCHAR(20),
    ProtocolNumber VARCHAR(20),
    OriginatingLocationId INTEGER,
    TotalVolume FLOAT,
    AvailableVolume FLOAT,
    VialCount INTEGER,
    LockedInRequestCount INTEGER,
    AtRepositoryCount INTEGER,
    AvailableCount INTEGER,
    ExpectedAvailableCount INTEGER,
    ParticipantSequenceKey VARCHAR(200),
    ProcessingLocation INT,
    FirstProcessedByInitials VARCHAR(32),

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
    GlobalUniqueId VARCHAR(50) NOT NULL,
    Volume FLOAT,
    SpecimenHash VARCHAR(256),
    Requestable BOOLEAN,
    CurrentLocation INT,
    AtRepository BOOLEAN NOT NULL DEFAULT FALSE,
    LockedInRequest BOOLEAN NOT NULL DEFAULT FALSE,
    Available BOOLEAN NOT NULL DEFAULT FALSE,
    ProcessingLocation INT,
    SpecimenId INTEGER NOT NULL,
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits VARCHAR(20),
    FirstProcessedByInitials VARCHAR(32),
    AvailabilityReason VARCHAR(256),

    CONSTRAINT PK_Specimens PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_GlobalId UNIQUE (GlobalUniqueId, Container),
    CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) REFERENCES study.Site(RowId),
    CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
);

CREATE INDEX IX_Vial_Container ON study.Vial(Container);
CREATE INDEX IX_Vial_GlobalUniqueId ON study.Vial(GlobalUniqueId);
CREATE INDEX IX_Vial_CurrentLocation ON study.Vial(CurrentLocation);
CREATE INDEX IX_Vial_Container_SpecimenHash ON study.Vial(Container, SpecimenHash);
CREATE INDEX IX_Vial_SpecimenId ON study.Vial(SpecimenId);

CREATE TABLE study.SpecimenEvent
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    VialId INT NOT NULL,
    LabId INT,
    UniqueSpecimenId VARCHAR(50),
    ParentSpecimenId INT,
    Stored INT,
    StorageFlag INT,
    StorageDate TIMESTAMP,
    ShipFlag INT,
    ShipBatchNumber INT,
    ShipDate TIMESTAMP,
    ImportedBatchNumber INT,
    LabReceiptDate TIMESTAMP,
    Comments VARCHAR(200),
    SpecimenCondition VARCHAR(30),
    SampleNumber INT,
    XSampleOrigin VARCHAR(50),
    ExternalLocation VARCHAR(50),
    UpdateTimestamp TIMESTAMP,
    OtherSpecimenId VARCHAR(50),
    ExpectedTimeUnit VARCHAR(15),
    ExpectedTimeValue FLOAT,
    GroupProtocol INT,
    RecordSource VARCHAR(20),
    freezer VARCHAR(200),
    fr_level1 VARCHAR(200),
    fr_level2 VARCHAR(200),
    fr_container VARCHAR(200),
    fr_position VARCHAR(200),
    SpecimenNumber VARCHAR(50),
    ExternalId INT,
    ShippedFromLab VARCHAR(32),
    ShippedToLab VARCHAR(32),
    Ptid VARCHAR(32),
    DrawTimestamp TIMESTAMP,
    SalReceiptDate TIMESTAMP,
    ClassId VARCHAR(20),
    VisitValue NUMERIC(15,4),
    ProtocolNumber VARCHAR(20),
    VisitDescription VARCHAR(10),
    Volume double precision,
    VolumeUnits VARCHAR(20),
    SubAdditiveDerivative VARCHAR(50),
    PrimaryTypeId INT,
    DerivativeTypeId INT,
    AdditiveTypeId INT,
    DerivativeTypeId2 INT,
    OriginatingLocationId INT,
    FrozenTime TIMESTAMP,
    ProcessingTime TIMESTAMP,
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits VARCHAR(20),
    ProcessedByInitials VARCHAR(32),
    ProcessingDate TIMESTAMP,

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
    Type VARCHAR(200),

    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

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

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);
CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);

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

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);
CREATE INDEX IX_Well_Container ON study.Well(Container);

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

/* study-10.20-10.30.sql */

ALTER TABLE exp.objectproperty DROP CONSTRAINT pk_objectproperty;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_object;
ALTER TABLE exp.objectproperty DROP CONSTRAINT fk_objectproperty_propertydescriptor;
DROP INDEX exp.idx_objectproperty_propertyid;

DELETE FROM exp.ObjectProperty
WHERE propertyid IN (SELECT DP.propertyid FROM exp.propertydomain DP JOIN exp.domaindescriptor D on DP.domainid = D.domainid JOIN study.dataset DS ON D.domainuri = DS.typeuri);

VACUUM FULL exp.ObjectProperty;

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

ALTER TABLE study.Study
    ADD COLUMN BlankQCStatePublic BOOLEAN NOT NULL DEFAULT FALSE;

/* study-10.30-11.10.sql */

ALTER TABLE study.SpecimenEvent ADD TotalCellCount FLOAT;
ALTER TABLE study.Vial ADD TotalCellCount FLOAT;

/* study-11.10-11.20.sql */

CREATE TABLE study.VisitAliases
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NOT NULL,
    SequenceNum NUMERIC(15, 4) NOT NULL,

    CONSTRAINT PK_VisitNames PRIMARY KEY (RowId)
);

CREATE UNIQUE INDEX UQ_VisitAliases_Name ON study.VisitAliases (Container, LOWER(Name));

-- Switch case-sensitive UNIQUE CONSTRAINTs to case-insensitive UNIQUE INDEXes

ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetName;
ALTER TABLE study.dataset DROP CONSTRAINT UQ_DatasetLabel;

CREATE UNIQUE INDEX UQ_DatasetName ON study.Dataset (Container, LOWER(Name));
CREATE UNIQUE INDEX UQ_DatasetLabel ON study.Dataset (Container, LOWER(Label));

-- named sets of normalization factors
CREATE TABLE study.ParticipantClassifications
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
    GroupProperty VARCHAR(200),

    CONSTRAINT pk_participantClassifications PRIMARY KEY (RowId)
);

-- represents a grouping category for a participant classification
CREATE TABLE study.ParticipantGroup
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,

	Label VARCHAR(200) NOT NULL,
    ClassificationId Integer NOT NULL,

    CONSTRAINT pk_participantGroup PRIMARY KEY (RowId),
    CONSTRAINT fk_participantClassifications_classificationId FOREIGN KEY (ClassificationId) REFERENCES study.ParticipantClassifications (RowId)
);

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

ALTER TABLE study.SpecimenEvent ADD TubeType VARCHAR(32);
ALTER TABLE study.Vial ADD TubeType VARCHAR(32);

-- Rename from ParticipantClassifications to ParticipantCategory (Singular)
ALTER TABLE study.participantclassifications
RENAME TO participantcategory;

-- Drop Foreign Key constraint
ALTER TABLE study.participantgroup
DROP CONSTRAINT fk_participantclassifications_classificationid;

-- Drop Primary Key constraint
ALTER TABLE study.participantcategory
DROP CONSTRAINT pk_participantclassifications;

-- Add Primary Key constraint
ALTER TABLE study.participantcategory
ADD CONSTRAINT pk_participantcategory PRIMARY KEY (rowid);

-- Rename foreign key column
ALTER TABLE study.participantgroup RENAME COLUMN classificationid TO categoryid;

-- Add Foreign Key constraint
ALTER TABLE study.participantgroup
ADD CONSTRAINT fk_participantcategory_categoryid FOREIGN KEY (categoryid)
REFERENCES study.participantcategory (rowid);

-- Add Unique constraint
ALTER TABLE study.participantcategory
ADD CONSTRAINT uq_label_container UNIQUE (label, container);

-- named sets of normalization factors
ALTER TABLE study.ParticipantCategory ADD ModifiedBy USERID;
ALTER TABLE study.ParticipantCategory ADD Modified TIMESTAMP;

UPDATE study.ParticipantCategory SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantCategory SET Modified = Created;

/* study-11.20-11.30.sql */

ALTER TABLE study.Study ADD Description text;

ALTER TABLE study.Study ADD ProtocolDocumentEntityId entityid;

ALTER TABLE study.Study ALTER COLUMN ProtocolDocumentEntityId SET NOT NULL;

-- populate the view category table with the dataset categories
INSERT INTO core.ViewCategory (Container, Label, CreatedBy, ModifiedBy)
  SELECT Container, Category, 0, 0 FROM study.Dataset WHERE LENGTH(Category) > 0 GROUP BY Container, Category;

ALTER TABLE study.Dataset ADD COLUMN CategoryId Integer;

UPDATE study.Dataset ds
    SET CategoryId = (SELECT rowId FROM core.ViewCategory vc WHERE ds.container = vc.container AND ds.category = vc.label);

-- drop the category column
ALTER TABLE study.Dataset DROP COLUMN Category;

ALTER TABLE study.Study ADD SourceStudyContainerId entityid;
ALTER TABLE study.Study ADD DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS';
ALTER TABLE study.Study ADD Investigator VARCHAR(200);
ALTER TABLE study.Study ADD StudyGrant VARCHAR(200);

ALTER TABLE study.Study RENAME COLUMN studyGrant TO "Grant";

/* study-11.30-12.10.sql */

ALTER TABLE study.Dataset ADD COLUMN Modified TIMESTAMP;

ALTER TABLE study.Dataset ADD COLUMN Type VARCHAR(50) NOT NULL DEFAULT 'Standard';

/* study-12.10-12.20.sql */

-- Rename 'ParticipantSequenceKey' to 'ParticipantSequenceNum' along with constraints and indices.
ALTER TABLE study.ParticipantVisit
  RENAME ParticipantSequenceKey TO ParticipantSequenceNum;
ALTER TABLE study.ParticipantVisit
  ADD CONSTRAINT UQ_ParticipantVisit_ParticipantSequenceNum UNIQUE (ParticipantSequenceNum, Container);
ALTER TABLE study.ParticipantVisit
  DROP CONSTRAINT UQ_StudyData_ParticipantSequenceKey;

ALTER INDEX study.IX_ParticipantVisit_ParticipantSequenceKey RENAME TO IX_ParticipantVisit_ParticipantSequenceNum;

ALTER TABLE study.Specimen
  RENAME ParticipantSequenceKey TO ParticipantSequenceNum;

ALTER INDEX study.IX_Specimen_ParticipantSequenceKey RENAME TO IX_Specimen_ParticipantSequenceNum;

ALTER TABLE study.participantgroup
  ADD COLUMN filters text,
  ADD COLUMN description varchar(250);

ALTER TABLE study.study ADD COLUMN DefaultTimepointDuration INT NOT NULL DEFAULT 1;

DELETE FROM study.study WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE study.Study
    ADD CONSTRAINT FK_Study_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

UPDATE study.StudyDesign SET sourceContainer=Container WHERE sourceContainer NOT IN (SELECT entityid FROM core.containers);

ALTER TABLE study.ParticipantGroup ADD COLUMN CreatedBy USERID;
ALTER TABLE study.ParticipantGroup ADD COLUMN Created TIMESTAMP;
ALTER TABLE study.ParticipantGroup ADD COLUMN ModifiedBy USERID;
ALTER TABLE study.ParticipantGroup ADD COLUMN Modified TIMESTAMP;

UPDATE study.ParticipantGroup SET CreatedBy = ParticipantCategory.CreatedBy FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;
UPDATE study.ParticipantGroup SET Created = ParticipantCategory.Created FROM study.ParticipantCategory WHERE CategoryId = ParticipantCategory.RowId;

UPDATE study.ParticipantGroup SET ModifiedBy = CreatedBy;
UPDATE study.ParticipantGroup SET Modified = Created;

/* study-12.20-12.30.sql */

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD COLUMN DateOffset INT NOT NULL DEFAULT CAST((RANDOM() * 364 + 1) AS INT);

-- Nullable... random alternate IDs are set via code
ALTER TABLE study.Participant ADD COLUMN AlternateId VARCHAR(32) NULL;

ALTER TABLE study.Cohort ADD COLUMN Enrolled BOOLEAN NOT NULL DEFAULT TRUE;

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;

-- Change some Specimen fields to bigint
ALTER TABLE study.specimenevent ALTER COLUMN rowid TYPE bigint;
ALTER TABLE study.specimenevent ALTER COLUMN vialid TYPE bigint;
ALTER TABLE study.vial ALTER COLUMN rowid TYPE bigint;
ALTER TABLE study.vial ALTER COLUMN specimenid TYPE bigint;
ALTER TABLE study.specimen ALTER COLUMN rowid TYPE bigint;

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

ALTER TABLE study.Study
    ADD StudySnapshot INT NULL,
    ADD LastSpecimenLoad TIMESTAMP NULL;  -- Helps determine whether a specimen refresh is needed

CREATE INDEX IX_Study_StudySnapshot ON study.Study(StudySnapshot);

-- Additional fields in Event and Vial tables
ALTER TABLE study.Vial ADD LatestComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestQualityComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestDeviationCode1 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode2 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode3 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestConcentration REAL;
ALTER TABLE study.Vial ADD LatestIntegrity REAL;
ALTER TABLE study.Vial ADD LatestRatio REAL;
ALTER TABLE study.Vial ADD LatestYield REAL;

ALTER TABLE study.SpecimenEvent ALTER COLUMN Comments TYPE VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD QualityComments VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD DeviationCode1 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode2 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode3 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD Concentration REAL;
ALTER TABLE study.SpecimenEvent ADD Integrity REAL;
ALTER TABLE study.SpecimenEvent ADD Ratio REAL;
ALTER TABLE study.SpecimenEvent ADD Yield REAL;

ALTER TABLE study.Visit ADD SequenceNumHandling VARCHAR(32) NULL;

ALTER TABLE study.SpecimenEvent ALTER COLUMN TubeType TYPE VARCHAR(64);
ALTER TABLE study.Vial ALTER COLUMN TubeType TYPE VARCHAR(64);

ALTER TABLE study.Vial ADD freezer VARCHAR(200);
ALTER TABLE study.Vial ADD fr_container VARCHAR(200);
ALTER TABLE study.Vial ADD fr_position VARCHAR(200);
ALTER TABLE study.Vial ADD fr_level1 VARCHAR(200);
ALTER TABLE study.Vial ADD fr_level2 VARCHAR(200);

/* study-12.30-13.10.sql */

ALTER TABLE study.Study ADD AllowReqLocRepository BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocClinic BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocSAL BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE study.Study ADD AllowReqLocEndpoint BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE study.specimenevent ALTER COLUMN externalid TYPE bigint;

ALTER TABLE study.study ADD ParticipantAliasDatasetName VARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasSourceColumnName VARCHAR(200);
ALTER TABLE study.study ADD ParticipantAliasColumnName VARCHAR(200);

ALTER TABLE study.study DROP ParticipantAliasDatasetName;

ALTER TABLE study.study ADD ParticipantAliasDatasetId INT;

ALTER TABLE study.study RENAME ParticipantAliasSourceColumnName TO ParticipantAliasSourceProperty;
ALTER TABLE study.study RENAME ParticipantAliasColumnName TO ParticipantAliasProperty;

-- Track participant indexing in the participant table now
ALTER TABLE study.Participant ADD LastIndexed TIMESTAMP NULL;

ALTER TABLE study.SpecimenDerivative ALTER COLUMN LdmsDerivativeCode TYPE VARCHAR(30);

ALTER TABLE study.SpecimenEvent ADD COLUMN InputHash BYTEA NULL;

/* study-13.10-13.20.sql */

-- To change the PK, it is more efficient to drop all other indexes (including unique constraints),
-- drop and recreate PK, and then rebuild indexes

-- Consider:  do we need a unique constraint on ParticipantSequenceNum if we have separate ones on Participant, SequenceNum ??
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

ALTER TABLE study.specimenevent ADD COLUMN obsolete BOOLEAN NOT NULL DEFAULT false;

/* study-13.20-13.30.sql */

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

-- Create an owner colunn to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD COLUMN OwnerId USERID NOT NULL DEFAULT -1;
UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE NOT shared;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);

-- Default the template type to nab if one isn't explicitly set
UPDATE study.Plate SET Type = 'NAb' WHERE Type IS NULL;

/* study-13.30-14.10.sql */

ALTER TABLE study.Visit ADD COLUMN Description TEXT;

CREATE SCHEMA specimenTables;

DROP TABLE study.specimenevent;
DROP TABLE study.vial;
DROP TABLE study.specimen;

CREATE SCHEMA studydesign;

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
  VisitRowId INT,

  Tag VARCHAR(200) NOT NULL,
  Description VARCHAR(200),

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_VisitRowId_Tag_Container PRIMARY KEY (VisitRowId, Tag, Container)
  --CONSTRAINT FK_Visit_VisitRowId FOREIGN KEY (VisitRowId, Container) REFERENCES study.Visit (RowId, Container)
);

-- new fields to add to existing cohort table
ALTER TABLE study.Cohort ADD COLUMN SubjectCount INT;
ALTER TABLE study.Cohort ADD COLUMN Description TEXT;

-- new fields to add to existing study properties table
ALTER TABLE study.Study ADD COLUMN Species VARCHAR(200);
ALTER TABLE study.Study ADD COLUMN EndDate TIMESTAMP;
ALTER TABLE study.Study ADD COLUMN AssayPlan TEXT;

-- new fields to add to existing visit table, default SequenceNumTarget to SequenceNumMin
ALTER TABLE study.Visit ADD COLUMN SequenceNumTarget NUMERIC(15,4) NOT NULL DEFAULT 0;
-- UPDATE study.Visit SET SequenceNumTarget = SequenceNumMin;  #19819: leave upgraded visits defaulting to 0

-- new fields to add to the existing site/location table
ALTER TABLE study.Site ADD COLUMN Description VARCHAR(500);
ALTER TABLE study.Site ADD COLUMN StreetAddress VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN City VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN GoverningDistrict VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN Country VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN PostalArea VARCHAR(50);

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

ALTER TABLE study.Study ADD COLUMN ShareDatasetDefinitions BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE study.AssaySpecimen ADD COLUMN Lab VARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleType VARCHAR(200);

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name TYPE VARCHAR(5);

ALTER TABLE study.visit RENAME COLUMN SequenceNumTarget TO ProtocolDay;
UPDATE study.visit SV SET ProtocolDay = Round((SV.SequenceNumMax + SV.SequenceNumMin)/2)
FROM study.study SS
WHERE SS.Container = SV.Container AND SS.TimePointType = 'DATE';