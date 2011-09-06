/*
 * Copyright (c) 2011 LabKey Corporation
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

-- Tables used for Study module

/* study-0.00-1.50.sql */

IF NOT EXISTS (SELECT * FROM sysusers WHERE name ='study')
    EXEC sp_addapprole 'study', 'password'
GO

IF NOT EXISTS (SELECT * FROM systypes WHERE name ='LSIDtype')
    EXEC sp_addtype 'LSIDtype', 'NVARCHAR(300)'
GO


CREATE TABLE study.Study
(
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Study PRIMARY KEY (Container)
)
GO


CREATE TABLE study.Site
(
    EntityId ENTITYID NOT NULL DEFAULT NEWID(),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    RowId INT IDENTITY(1,1),
    ScharpId INT,
    LdmsLabCode INT,
    LabwareLabCode NVARCHAR(20),
    LabUploadCode NVARCHAR(2),
    IsSal Bit,
    IsRepository Bit,
    IsEndpoint Bit,

    CONSTRAINT PK_Site PRIMARY KEY (RowId)
)
GO


CREATE TABLE study.Visit
(
    VisitId INT NOT NULL,
    Label NVARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Container ENTITYID NOT NULL

    CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
)
GO


CREATE TABLE study.VisitMap
(
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,    -- FK
    DataSetId INT NOT NULL,    -- FK
    IsRequired BIT NOT NULL DEFAULT 1

    CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
)
GO


CREATE TABLE study.DataSet -- AKA CRF or Assay
(
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI NVARCHAR(200) NULL,
    Label NVARCHAR(200) NULL,
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Category NVARCHAR(200) NULL

    CONSTRAINT PK_DataSet PRIMARY KEY CLUSTERED (Container,DataSetId)
)
GO


-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(16) NOT NULL,

    CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
)
GO

CREATE TABLE study.SampleRequestStatus
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),

    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
)
GO


CREATE TABLE study.SampleRequestActor
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    PerSite Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
)


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

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
)
GO

CREATE TABLE study.SampleRequestRequirement
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description NVARCHAR(300),
    Complete Bit NOT NULL DEFAULT 0,

    CONSTRAINT PK_SampleRequestRequirement PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId),
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (SiteId) REFERENCES study.Site(RowId)
)
GO


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

    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
)
GO


CREATE TABLE study.Report
(
    ReportId INT IDENTITY NOT NULL,
    EntityId ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ContainerId ENTITYID NOT NULL,
    Label NVARCHAR(100) NOT NULL,
    Params NVARCHAR(512) NULL,
    ReportType NVARCHAR(100) NOT NULL,
    Scope INT NOT NULL,

    CONSTRAINT PK_Report PRIMARY KEY (ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
)
GO


CREATE TABLE study.StudyData
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitId INT NULL,
    DatasetId INT NOT NULL,
    LSID VARCHAR(200) NOT NULL,

    CONSTRAINT PK_ParticipantDataset PRIMARY KEY (LSID),
    CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
GO


CREATE TABLE study.SpecimenAdditive
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsAdditiveCode NVARCHAR(3),
    LabwareAdditiveCode NVARCHAR(20),
    Additive NVARCHAR(100),

    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.SpecimenDerivative
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsDerivativeCode NVARCHAR(3),
    LabwareDerivativeCode NVARCHAR(20),
    Derivative NVARCHAR(100),

    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.SpecimenPrimaryType
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    PrimaryTypeLdmsCode NVARCHAR(5),
    PrimaryTypeLabwareCode NVARCHAR(5),
    PrimaryType NVARCHAR(100),

    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container)
)
GO

CREATE TABLE study.Specimen
(
    RowId INT NOT NULL, -- FK exp.Material
    Container ENTITYID NOT NULL,
    RecordSource NVARCHAR(10),
    GlobalUniqueId NVARCHAR(20) NOT NULL,
    Ptid INT,
    DrawTimestamp DATETIME,
    SalReceiptDate DATETIME,
    SpecimenNumber NVARCHAR(50),
    ClassId NVARCHAR(4),
    VisitValue FLOAT,
    ProtocolNumber NVARCHAR(10),
    VisitDescription NVARCHAR(3),
    OtherSpecimenId NVARCHAR(20),
    Volume FLOAT,
    VolumeUnits NVARCHAR(3),
    ExpectedTimeValue FLOAT,
    ExpectedTimeUnit NVARCHAR(15),
    GroupProtocol INT,
    SubAdditiveDerivative NVARCHAR(20),
    PrimaryTypeId INT,
    DerivativeTypeId INT,
    AdditiveTypeId INT,
    SpecimenCondition NVARCHAR(3),
    SampleNumber INT,
    XSampleOrigin NVARCHAR(20),
    ExternalLocation NVARCHAR(20),
    UpdateTimestamp DATETIME,

    CONSTRAINT PK_Specimens PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_GlobalId UNIQUE (GlobalUniqueId, Container),
    CONSTRAINT FK_Specimens_Additives FOREIGN KEY (AdditiveTypeId) REFERENCES study.SpecimenAdditive(RowId),
    CONSTRAINT FK_Specimens_Derivatives FOREIGN KEY (DerivativeTypeId) REFERENCES study.SpecimenDerivative(RowId),
    CONSTRAINT FK_Specimens_PrimaryTypes FOREIGN KEY (PrimaryTypeId) REFERENCES study.SpecimenPrimaryType(RowId)
)
GO

CREATE TABLE study.SpecimenEvent
(
    RowId INT IDENTITY(1, 1),
    Container ENTITYID NOT NULL,
    ScharpId INT,
    SpecimenId INT NOT NULL,
    LabId INT,
    UniqueSpecimenId NVARCHAR(20),
    ParentSpecimenId INT,
    Stored INT,
    StorageFlag INT,
    StorageDate DATETIME,
    ShipFlag INT,
    ShipBatchNumber INT,
    ShipDate DATETIME,
    ImportedBatchNumber INT,
    LabReceiptDate DATETIME,
    Comments NVARCHAR(30),

    CONSTRAINT PK_SpecimensEvents PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_ScharpId UNIQUE (ScharpId, Container),
    CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId),
    CONSTRAINT FK_Specimens_Site FOREIGN KEY (LabId) REFERENCES study.Site(RowId)
)
GO

CREATE TABLE study.SampleRequestSpecimen
(
    RowId INT IDENTITY(1, 1),
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenId INT NOT NULL,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestSpecimen_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
)
GO

ALTER TABLE study.StudyData ADD Created DATETIME NULL, Modified DATETIME NULL, VisitDate DATETIME NULL
GO

ALTER TABLE study.DataSet ADD EntityId ENTITYID
GO

ALTER TABLE study.Study ADD EntityId ENTITYID
GO

ALTER TABLE study.Report DROP COLUMN Created
GO

ALTER TABLE study.Report ADD Created DATETIME
GO

CREATE TABLE study.AssayRun
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    AssayType NVARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
)
GO

ALTER TABLE study.SampleRequestStatus ADD
    FinalState BIT NOT NULL DEFAULT 0,
    SpecimensLocked BIT NOT NULL DEFAULT 1
GO

ALTER TABLE study.Specimen
    ALTER COLUMN Ptid NVARCHAR(32)
GO

ALTER TABLE study.Participant ADD
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,

    CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
    CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
GO

ALTER TABLE study.SpecimenEvent
    DROP CONSTRAINT UQ_Specimens_ScharpId
GO

/* study-1.50-1.60.sql */

CREATE TABLE study.UploadLog
(
    RowId INT IDENTITY NOT NULL,
    Container ENTITYID NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy USERID NOT NULL,
    Description TEXT,
    FilePath VARCHAR(512),
    DatasetId INT NOT NULL,
    Status VARCHAR(20),

    CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
)
GO

ALTER TABLE study.Report
    ADD ShowWithDataset INT NULL
GO

-- Change visit ids to type numeric:
ALTER TABLE study.Visit
    DROP CONSTRAINT PK_Visit
GO
ALTER TABLE study.Visit
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.Visit
    ADD CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
GO


ALTER TABLE study.VisitMap
    DROP CONSTRAINT PK_VisitMap
GO
ALTER TABLE study.VisitMap
    ALTER COLUMN VisitId NUMERIC(15,4) NOT NULL
GO
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
GO


ALTER TABLE study.studydata
    DROP CONSTRAINT AK_ParticipantDataset
GO
ALTER TABLE study.studydata
    ALTER COLUMN VisitId NUMERIC(15,4) NULL
GO
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
GO

ALTER TABLE study.Specimen
    ALTER COLUMN VisitValue NUMERIC(15,4)
GO

ALTER TABLE study.Visit ADD VisitDateDatasetId INT
GO
ALTER TABLE study.DataSet ADD VisitDatePropertyName NVARCHAR(200)
GO

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
GO

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD VisitRowId INT NOT NULL DEFAULT -1;
GO
ALTER TABLE study.VisitMap DROP COLUMN VisitId;
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);
GO

--
-- fix up ParticipantVisit
--

CREATE TABLE study.ParticipantVisit
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId INT NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate DATETIME NULL
);
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO

--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD SequenceNum Numeric(15,4);
GO
ALTER TABLE study.StudyData DROP AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, SequenceNum, ParticipantId);
GO

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;
GO

ALTER TABLE study.StudyData
    ADD SourceLSID VARCHAR(200) NULL
GO
ALTER TABLE study.DataSet ADD KeyPropertyName NVARCHAR(50) NULL         -- Property name in TypeURI
GO

ALTER TABLE study.StudyData ADD _key NVARCHAR(200) NULL                 -- assay key column, used only on INSERT for UQ index
GO

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
GO

ALTER TABLE study.StudyData
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);
GO

-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate DATETIME NULL
GO
ALTER TABLE study.StudyData DROP COLUMN VisitDate
GO

ALTER TABLE study.SampleRequestSpecimen ADD
    SpecimenGlobalUniqueId NVARCHAR(100)
GO

ALTER TABLE study.Specimen DROP COLUMN
    SpecimenCondition,
    SampleNumber,
    XSampleOrigin,
    ExternalLocation,
    UpdateTimestamp,
    OtherSpecimenId,
    ExpectedTimeUnit,
    RecordSource,
    GroupProtocol,
    ExpectedTimeValue
GO

ALTER TABLE study.SpecimenEvent ADD
    SpecimenCondition NVARCHAR(3),
    SampleNumber INT,
    XSampleOrigin NVARCHAR(20),
    ExternalLocation NVARCHAR(20),
    UpdateTimestamp DATETIME,
    OtherSpecimenId NVARCHAR(20),
    ExpectedTimeUnit NVARCHAR(15),
    ExpectedTimeValue FLOAT,
    GroupProtocol INT,
    RecordSource NVARCHAR(10)
GO

-- fix up study.SampleRequestSpecimen
ALTER TABLE study.SampleRequestSpecimen DROP CONSTRAINT fk_SampleRequestSpecimen_specimen;
ALTER TABLE study.SampleRequestSpecimen DROP COLUMN SpecimenId;
GO

ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Additives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_PrimaryTypes;
GO

ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
CREATE INDEX IX_SpecimenAdditive_ScharpId ON study.SpecimenAdditive(ScharpId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container);
GO

ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
CREATE INDEX IX_SpecimenDerivative_ScharpId ON study.SpecimenDerivative(ScharpId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container);
GO

ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
CREATE INDEX IX_SpecimenPrimaryType_ScharpId ON study.SpecimenPrimaryType(ScharpId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container);
GO

CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(SpecimenId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);
GO

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

ALTER TABLE study.Report DROP CONSTRAINT PK_Report
GO
ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId)
GO

CREATE INDEX IX_AssayRun_Container ON study.AssayRun(Container);

CREATE INDEX IX_Plate_Container ON study.Plate(Container);

CREATE INDEX IX_SampleRequest_Container ON study.SampleRequest(Container);

CREATE INDEX IX_SampleRequest_StatusId ON study.SampleRequest(StatusId);

CREATE INDEX IX_SampleRequest_DestinationSiteId ON study.SampleRequest(DestinationSiteId);

CREATE INDEX IX_SampleRequestActor_Container ON study.SampleRequestActor(Container);

CREATE INDEX IX_SampleRequestEvent_Container ON study.SampleRequestEvent(Container);

CREATE INDEX IX_SampleRequestEvent_RequestId ON study.SampleRequestEvent(RequestId);

CREATE INDEX IX_SampleRequestRequirement_Container ON study.SampleRequestRequirement(Container);

CREATE INDEX IX_SampleRequestRequirement_RequestId ON study.SampleRequestRequirement(RequestId);

CREATE INDEX IX_SampleRequestRequirement_ActorId ON study.SampleRequestRequirement(ActorId);

CREATE INDEX IX_SampleRequestRequirement_SiteId ON study.SampleRequestRequirement(SiteId);

CREATE INDEX IX_SampleRequestSpecimen_Container ON study.SampleRequestSpecimen(Container);

CREATE INDEX IX_SampleRequestSpecimen_SampleRequestId ON study.SampleRequestSpecimen(SampleRequestId);

CREATE INDEX IX_SampleRequestSpecimen_SpecimenGlobalUniqueId ON study.SampleRequestSpecimen(SpecimenGlobalUniqueId);

CREATE INDEX IX_SampleRequestStatus_Container ON study.SampleRequestStatus(Container);

CREATE INDEX IX_Specimen_Container ON study.Specimen(Container);

CREATE INDEX IX_Specimen_GlobalUniqueId ON study.Specimen(GlobalUniqueId);

CREATE INDEX IX_SpecimenAdditive_Container ON study.SpecimenAdditive(Container);

CREATE INDEX IX_SpecimenDerivative_Container ON study.SpecimenDerivative(Container);

CREATE INDEX IX_SpecimenPrimaryType_Container ON study.SpecimenPrimaryType(Container);

CREATE INDEX IX_SpecimenEvent_Container ON study.SpecimenEvent(Container);

CREATE INDEX IX_SpecimenEvent_LabId ON study.SpecimenEvent(LabId);

CREATE INDEX IX_Well_PlateId ON study.Well(PlateId);

CREATE INDEX IX_Well_Container ON study.Well(Container);

CREATE INDEX IX_WellGroup_PlateId ON study.WellGroup(PlateId);

CREATE INDEX IX_WellGroup_Container ON study.WellGroup(Container);

GO
/*
 * Fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3111
 */

-- VISITMAP
EXEC sp_rename 'study.visitmap.isrequired', 'required', 'COLUMN';
GO

-- DATASET

ALTER TABLE study.dataset ADD name VARCHAR(200);
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

ALTER TABLE study.StudyDesign
    ADD CONSTRAINT DF_Active DEFAULT 0 FOR Active
GO

ALTER TABLE study.StudyDesign
    DROP COLUMN StudyEntityId
GO

ALTER TABLE study.StudyDesign
    ADD SourceContainer ENTITYID
GO

ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;
GO

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);
GO

DROP TABLE study.AssayRun
GO

ALTER TABLE study.Specimen
    ADD Requestable BIT

ALTER TABLE study.SpecimenEvent ADD
    freezer NVARCHAR(200),
    fr_level1 NVARCHAR(200),
    fr_level2 NVARCHAR(200),
    fr_container NVARCHAR(200),
    fr_position NVARCHAR(200)
GO

ALTER TABLE study.Study ADD
    DateBased BIT DEFAULT 0,
    StartDate DATETIME
GO

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
GO

ALTER TABLE study.Participant ADD
    StartDate DATETIME
GO

ALTER TABLE study.Dataset
    ADD DemographicData BIT
GO

ALTER TABLE study.Dataset
    ADD CONSTRAINT DF_DemographicData_False
    DEFAULT 0 FOR DemographicData
GO

ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
GO

ALTER TABLE study.Plate ADD Type NVARCHAR(200)
GO

CREATE TABLE study.Cohort
(
    RowId INT IDENTITY(1,1),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
)
GO

ALTER TABLE study.Dataset ADD
    CohortId INT NULL,
    CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId)
GO

ALTER TABLE study.Participant ADD
    CohortId INT NULL,
    CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
GO

CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);
GO

ALTER TABLE study.Visit ADD
    CohortId INT NULL,
    CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId)
GO

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);
GO

ALTER TABLE study.Study ADD
    ParticipantCohortDataSetId INT NULL,
    ParticipantCohortProperty NVARCHAR(200) NULL;
GO

ALTER TABLE study.Participant
    DROP CONSTRAINT PK_Participant
GO

DROP INDEX study.Participant.IX_Participant_ParticipantId
GO    

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.Participant
    ADD CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
GO    

CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId)
GO

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments NVARCHAR(200)
GO

ALTER TABLE study.ParticipantVisit
    DROP CONSTRAINT PK_ParticipantVisit
GO

ALTER TABLE study.ParticipantVisit
    ALTER COLUMN ParticipantId NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);
GO

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
)
GO

ALTER TABLE study.specimen ADD CurrentLocation INT;
ALTER TABLE study.specimen ADD
    CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId)
GO

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);
GO

ALTER TABLE study.Dataset ADD
    KeyPropertyManaged BIT DEFAULT 0
GO

ALTER TABLE study.Study ADD
    DatasetRowsEditable BIT DEFAULT 0
GO

UPDATE study.Study
    SET DatasetRowsEditable = 0 WHERE DatasetRowsEditable IS NULL
GO

UPDATE study.Dataset
    SET KeyPropertyManaged = 0 WHERE KeyPropertyManaged IS NULL
GO

ALTER TABLE study.Study
    ALTER COLUMN DatasetRowsEditable BIT NOT NULL
GO

ALTER TABLE study.Dataset
    ALTER COLUMN KeyPropertyManaged BIT NOT NULL
GO

ALTER TABLE study.Study
    ADD SecurityType NVARCHAR(32)
GO

UPDATE study.Study
    SET SecurityType = 'ADVANCED' WHERE StudySecurity = 1

UPDATE study.Study
    SET SecurityType = 'EDITABLE_DATASETS' WHERE StudySecurity = 0 AND DatasetRowsEditable = 1

UPDATE study.Study
    SET SecurityType = 'BASIC' WHERE StudySecurity = 0 AND DatasetRowsEditable = 0
GO

declare @constname sysname
select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'StudySecurity'

declare @cmd VARCHAR(500)
select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
  DROP COLUMN StudySecurity

select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('Study.Study')
and col_name(soc.id, sc.colid) = 'DatasetRowsEditable'

select @cmd='Alter Table Study.Study DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE study.Study
    DROP COLUMN DatasetRowsEditable
GO

ALTER TABLE study.Study
    ALTER COLUMN SecurityType NVARCHAR(32) NOT NULL
GO

ALTER TABLE study.Cohort
    ADD LSID NVARCHAR(200)
GO

UPDATE study.Cohort
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE study.Cohort
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

ALTER TABLE study.Visit
    ADD LSID NVARCHAR(200)
GO

UPDATE study.Visit
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE
    study.Visit
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

ALTER TABLE study.Study
    ADD LSID NVARCHAR(200)
GO

UPDATE study.Study
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL
GO

ALTER TABLE
    study.Study
    ALTER COLUMN LSID NVARCHAR(200) NOT NULL
GO

ALTER TABLE
    study.Study
    ADD ManualCohortAssignment BIT NOT NULL DEFAULT 0
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
)
GO

ALTER TABLE study.StudyData ADD
    QCState INT NULL,
    CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId)

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState)
GO

ALTER TABLE study.Study ADD
    DefaultPipelineQCState INT,
    DefaultAssayQCState INT,
    DefaultDirectEntryQCState INT,
    ShowPrivateDataByDefault BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId)
GO

ALTER TABLE
    study.Visit
    DROP COLUMN LSID
GO

UPDATE study.Study
  SET SecurityType = 'BASIC_READ' WHERE SecurityType = 'BASIC'

UPDATE study.Study
  SET SecurityType = 'BASIC_WRITE' WHERE SecurityType = 'EDITABLE_DATASETS'

UPDATE study.Study
  SET SecurityType = 'ADVANCED_READ' WHERE SecurityType = 'ADVANCED'
GO

CREATE TABLE study.SpecimenComment
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SpecimenNumber NVARCHAR(50) NOT NULL,
    GlobalUniqueId NVARCHAR(50) NOT NULL,
    CreatedBy USERID,
    Created DATETIME,
    ModifiedBy USERID,
    Modified DATETIME,
    Comment NTEXT,

    CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
);

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);

UPDATE study.dataset
    SET label = name WHERE label IS NULL;

ALTER TABLE study.dataset
    ALTER COLUMN label NVARCHAR(200) NOT NULL;

ALTER TABLE study.dataset ADD
    CONSTRAINT UQ_DatasetLabel UNIQUE (container, label);
GO

ALTER TABLE study.Specimen
    ADD SpecimenHash NVARCHAR(256);
GO

DROP INDEX study.SpecimenComment.IX_SpecimenComment_SpecimenNumber;
GO

ALTER TABLE study.SpecimenComment
    ADD SpecimenHash NVARCHAR(256);

ALTER TABLE study.SpecimenComment
    DROP COLUMN SpecimenNumber;
GO

CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);
CREATE INDEX IX_Specimen_SpecimenHash ON study.Specimen(Container, SpecimenHash);
GO

/* study-8.30-9.10.sql */

ALTER TABLE study.Dataset
  ADD protocolid INT NULL;
GO

ALTER TABLE study.SpecimenEvent
    ADD SpecimenNumber NVARCHAR(50);
GO

UPDATE study.SpecimenEvent SET SpecimenNumber =
    (SELECT SpecimenNumber FROM study.Specimen WHERE study.SpecimenEvent.SpecimenId = study.Specimen.RowId);
GO

ALTER TABLE study.Specimen
    DROP COLUMN SpecimenNumber;
GO

EXEC core.fn_dropifexists 'Visit', 'study', 'INDEX', 'IX_Visit_ContainerSeqNum'
go
EXEC core.fn_dropifexists 'Visit', 'study', 'INDEX', 'IX_Visit_SequenceNumMin'
go
ALTER TABLE study.Visit ADD CONSTRAINT UQ_Visit_ContSeqNum UNIQUE (Container, SequenceNumMin)
go

UPDATE exp.protocol SET MaxInputMaterialPerInstance = NULL
    WHERE
        (
            Lsid LIKE '%:LuminexAssayProtocol.Folder-%' OR
            Lsid LIKE '%:GeneralAssayProtocol.Folder-%' OR
            Lsid LIKE '%:NabAssayProtocol.Folder-%' OR 
            Lsid LIKE '%:ElispotAssayProtocol.Folder-%' OR 
            Lsid LIKE '%:MicroarrayAssayProtocol.Folder-%'
        ) AND ApplicationType = 'ExperimentRun'
GO

-- Remove ScharpId from SpecimenPrimaryType
ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
DROP INDEX study.SpecimenPrimaryType.IX_SpecimenPrimaryType_ScharpId;
ALTER TABLE study.SpecimenPrimaryType ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenPrimaryType SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenPrimaryType DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenPrimaryType_ExternalId ON study.SpecimenPrimaryType(ExternalId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryType UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from SpecimenDerivativeType
ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
DROP INDEX study.SpecimenDerivative.IX_SpecimenDerivative_ScharpId;
ALTER TABLE study.SpecimenDerivative ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenDerivative SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenDerivative DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenDerivative_ExternalId ON study.SpecimenDerivative(ExternalId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivative UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from SpecimenAdditive
ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
DROP INDEX study.SpecimenAdditive.IX_SpecimenAdditive_ScharpId;
ALTER TABLE study.SpecimenAdditive ADD ExternalId INT NOT NULL DEFAULT 0;
GO
UPDATE study.SpecimenAdditive SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenAdditive DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenAdditive_ExternalId ON study.SpecimenAdditive(ExternalId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additive UNIQUE (ExternalId, Container);
GO

-- Remove ScharpId from Site
ALTER TABLE study.Site ADD ExternalId INT;
GO
UPDATE study.Site SET ExternalId = ScharpId;
ALTER TABLE study.Site DROP COLUMN ScharpId;
GO

-- Remove ScharpId from SpecimenEvent
ALTER TABLE study.SpecimenEvent ADD ExternalId INT;
GO
UPDATE study.SpecimenEvent SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenEvent DROP COLUMN ScharpId;
GO

UPDATE study.Specimen SET
    PrimaryTypeId = (SELECT RowId FROM study.SpecimenPrimaryType WHERE
        ExternalId = study.Specimen.PrimaryTypeId AND study.SpecimenPrimaryType.Container = study.Specimen.Container),
    DerivativeTypeId = (SELECT RowId FROM study.SpecimenDerivative WHERE
        ExternalId = study.Specimen.DerivativeTypeId AND study.SpecimenDerivative.Container = study.Specimen.Container),
    AdditiveTypeId = (SELECT RowId FROM study.SpecimenAdditive WHERE
        ExternalId = study.Specimen.AdditiveTypeId AND study.SpecimenAdditive.Container = study.Specimen.Container);
GO

ALTER TABLE study.Site ADD
    Repository BIT,
    Clinic BIT,
    SAL BIT,
    Endpoint BIT;
GO

UPDATE study.Site SET Repository = IsRepository, Clinic = IsClinic, SAL = IsSAL, Endpoint = IsEndpoint;

ALTER TABLE study.Site DROP COLUMN
    IsRepository,
    IsClinic,
    IsSAL,
    IsEndpoint;
GO

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

ALTER TABLE study.Specimen ADD
    DerivativeTypeId2 INT,
    FrozenTime DATETIME,
    ProcessingTime DATETIME,
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20),
    CONSTRAINT FK_Specimens_Derivatives2 FOREIGN KEY (DerivativeTypeId2) REFERENCES study.SpecimenDerivative(RowId)

GO

ALTER TABLE study.SpecimenEvent ADD
    ShippedFromLab INT,
    ShippedToLab INT,
    CONSTRAINT FK_ShippedFromLab_Site FOREIGN KEY (ShippedFromLab) references study.Site(RowId),
    CONSTRAINT FK_ShippedToLab_Site FOREIGN KEY (ShippedToLab) references study.Site(RowId)

GO

CREATE INDEX IX_SpecimenEvent_ShippedFromLab ON study.SpecimenEvent(ShippedFromLab)
CREATE INDEX IX_SpecimenEvent_ShippedToLab ON study.SpecimenEvent(ShippedToLab)

GO

ALTER TABLE study.Site ALTER COLUMN LabUploadCode NVARCHAR(10);
GO

ALTER TABLE study.SpecimenAdditive ALTER COLUMN LdmsAdditiveCode NVARCHAR(30);
GO

ALTER TABLE study.SpecimenDerivative ALTER COLUMN LdmsDerivativeCode NVARCHAR(20);
GO

ALTER TABLE study.Specimen ALTER COLUMN GlobalUniqueId NVARCHAR(50) NOT NULL;
ALTER TABLE study.Specimen ALTER COLUMN ClassId NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN ProtocolNumber NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN VisitDescription NVARCHAR(10);
ALTER TABLE study.Specimen ALTER COLUMN VolumeUnits NVARCHAR(20);
ALTER TABLE study.Specimen ALTER COLUMN SubAdditiveDerivative NVARCHAR(50);
GO

ALTER TABLE study.SpecimenEvent ALTER COLUMN UniqueSpecimenId NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN RecordSource NVARCHAR(20);
ALTER TABLE study.SpecimenEvent ALTER COLUMN OtherSpecimenId NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN XSampleOrigin NVARCHAR(50);
ALTER TABLE study.SpecimenEvent ALTER COLUMN SpecimenCondition NVARCHAR(30);
ALTER TABLE study.SpecimenEvent ALTER COLUMN ExternalLocation NVARCHAR(50);
GO

-- Migrate batch properties from runs to separate batch objects

-- Create batch rows
INSERT INTO exp.experiment (lsid, name, created, createdby, modified, modifiedby, container, hidden, batchprotocolid)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.name + ' Batch', er.created, er.createdby, er.modified, er.modifiedby, er.container, 0, p.rowid FROM exp.experimentrun er, exp.protocol p
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%' AND er.protocollsid = p.lsid
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Add an entry to the object table
INSERT INTO exp.object (objecturi, container)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.container FROM exp.experimentrun er
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Flip the properties to hang from the batch
UPDATE exp.ObjectProperty SET ObjectId =
    (SELECT oBatch.ObjectId
        FROM exp.Object oRun, exp.Object oBatch WHERE exp.ObjectProperty.ObjectId = oRun.ObjectId AND
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(oRun.ObjectURI, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment') = oBatch.ObjectURI
    )
WHERE
    PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%')
    AND ObjectId IN (SELECT o.ObjectId FROM exp.Object o, exp.ExperimentRun er WHERE o.ObjectURI = er.LSID AND er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
    AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL))
GO

-- Point the runs at their new batches
INSERT INTO exp.RunList (ExperimentRunId, ExperimentId)
    SELECT er.RowId, e.RowId FROM exp.ExperimentRun er, exp.Experiment e
    WHERE
        e.LSID = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.LSID, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment')
        AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL)
GO

-- Clean out the duplicated batch properties on the runs
DELETE FROM exp.ObjectProperty
    WHERE
        ObjectId IN (SELECT o.ObjectId FROM exp.Object o WHERE o.ObjectURI LIKE 'urn:lsid:%:%AssayRun.Folder-%:%')
        AND PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%')
GO

/* study-9.10-9.20.sql */

ALTER TABLE study.SpecimenComment ADD
  QualityControlFlag BIT NOT NULL DEFAULT 0,
  QualityControlFlagForced BIT NOT NULL DEFAULT 0,
  QualityControlComments NVARCHAR(512)
GO

ALTER TABLE study.SpecimenEvent ADD
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
  PrimaryVolumeUnits NVARCHAR(20)
GO

UPDATE study.SpecimenEvent SET
    study.SpecimenEvent.Ptid = study.Specimen.Ptid,
    study.SpecimenEvent.DrawTimestamp = study.Specimen.DrawTimestamp,
    study.SpecimenEvent.SalReceiptDate = study.Specimen.SalReceiptDate,
    study.SpecimenEvent.ClassId = study.Specimen.ClassId,
    study.SpecimenEvent.VisitValue = study.Specimen.VisitValue,
    study.SpecimenEvent.ProtocolNumber = study.Specimen.ProtocolNumber,
    study.SpecimenEvent.VisitDescription = study.Specimen.VisitDescription,
    study.SpecimenEvent.Volume = study.Specimen.Volume,
    study.SpecimenEvent.VolumeUnits = study.Specimen.VolumeUnits,
    study.SpecimenEvent.SubAdditiveDerivative = study.Specimen.SubAdditiveDerivative,
    study.SpecimenEvent.PrimaryTypeId = study.Specimen.PrimaryTypeId,
    study.SpecimenEvent.DerivativeTypeId = study.Specimen.DerivativeTypeId,
    study.SpecimenEvent.AdditiveTypeId = study.Specimen.AdditiveTypeId,
    study.SpecimenEvent.DerivativeTypeId2 = study.Specimen.DerivativeTypeId2,
    study.SpecimenEvent.OriginatingLocationId = study.Specimen.OriginatingLocationId,
    study.SpecimenEvent.FrozenTime = study.Specimen.FrozenTime,
    study.SpecimenEvent.ProcessingTime = study.Specimen.ProcessingTime,
    study.SpecimenEvent.PrimaryVolume = study.Specimen.PrimaryVolume,
    study.SpecimenEvent.PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen WHERE study.Specimen.RowId = study.SpecimenEvent.SpecimenId
GO

CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);
GO

ALTER TABLE study.SampleRequestSpecimen
  ADD Orphaned BIT NOT NULL DEFAULT 0
GO

UPDATE study.SampleRequestSpecimen SET Orphaned = 1 WHERE RowId IN (
    SELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen
    LEFT OUTER JOIN study.Specimen ON
        study.Specimen.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND
        study.Specimen.Container = study.SampleRequestSpecimen.Container
    WHERE study.Specimen.GlobalUniqueId IS NULL
)
GO

-- It was a mistake to make this a "hard" foreign key- query will take care of
-- linking without it, so we just need an index.  This allows us to drop all contents
-- of the table when reloading.
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives2
GO

CREATE INDEX IX_Specimens_Derivatives2 ON study.Specimen(DerivativeTypeId2)
GO

ALTER TABLE study.Specimen ADD
  AtRepository BIT NOT NULL DEFAULT 0,
  LockedInRequest BIT NOT NULL DEFAULT 0,
  Available BIT NOT NULL DEFAULT 0
GO

UPDATE study.Specimen SET AtRepository = 1
  WHERE CurrentLocation IN (SELECT ss.RowId FROM study.Site ss WHERE ss.Repository = 1)
GO

UPDATE study.Specimen SET LockedInRequest = 1 WHERE RowId IN
(
  SELECT study.Specimen.RowId FROM ((study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = 1)
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = 0)
      JOIN study.Specimen ON study.Specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId AND study.Specimen.Container = map.Container
)
GO

UPDATE study.Specimen SET Available =
(
  CASE Requestable
  WHEN 1 THEN (
      CASE LockedInRequest
      WHEN 1 THEN 0
      ELSE 1
      END)
  WHEN 0 THEN 0
  ELSE (
  CASE AtRepository
      WHEN 1 THEN (
          CASE LockedInRequest
          WHEN 1 THEN 0
          ELSE 1
          END)
      ELSE 0
      END)
  END
)
GO

ALTER TABLE study.Specimen ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME,
  ProcessingLocation INT
GO

DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedFromLab
GO
DROP INDEX study.SpecimenEvent.IX_SpecimenEvent_ShippedToLab
GO

ALTER TABLE study.SpecimenEvent ADD
  ProcessedByInitials NVARCHAR(32),
  ProcessingDate DATETIME
GO

ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedFromLab_Site
GO
ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedToLab_Site
GO

ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedFromLab NVARCHAR(32)
GO
ALTER TABLE study.SpecimenEvent ALTER COLUMN ShippedToLab NVARCHAR(32)
GO

ALTER TABLE study.Study ADD
  AllowReload BIT NOT NULL DEFAULT 0,
  ReloadInterval INT NULL,
  LastReload DATETIME NULL;

ALTER TABLE study.Study ADD
  ReloadUser UserId;

-- This script creates a hard table to hold static specimen data.  Dynamic data (available counts, etc)
-- is calculated on the fly via aggregates.

UPDATE study.Specimen SET SpecimenHash =
(SELECT
    'Fld-' + CAST(core.Containers.RowId AS NVARCHAR)
    +'~'+ CASE WHEN OriginatingLocationId IS NOT NULL THEN CAST(OriginatingLocationId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN Ptid IS NOT NULL THEN CAST(Ptid AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DrawTimestamp IS NOT NULL THEN CAST(DrawTimestamp AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN SalReceiptDate IS NOT NULL THEN CAST(SalReceiptDate AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN ClassId IS NOT NULL THEN CAST(ClassId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VisitValue IS NOT NULL THEN CAST(VisitValue AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN ProtocolNumber IS NOT NULL THEN CAST(ProtocolNumber AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryVolume IS NOT NULL THEN CAST(PrimaryVolume AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryVolumeUnits IS NOT NULL THEN CAST(PrimaryVolumeUnits AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VisitDescription IS NOT NULL THEN CAST(VisitDescription AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN VolumeUnits IS NOT NULL THEN CAST(VolumeUnits AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN SubAdditiveDerivative IS NOT NULL THEN CAST(SubAdditiveDerivative AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN PrimaryTypeId IS NOT NULL THEN CAST(PrimaryTypeId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DerivativeTypeId IS NOT NULL THEN CAST(DerivativeTypeId AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN DerivativeTypeId2 IS NOT NULL THEN CAST(DerivativeTypeId2 AS NVARCHAR) ELSE '' END
    +'~'+ CASE WHEN AdditiveTypeId IS NOT NULL THEN CAST(AdditiveTypeId AS NVARCHAR) ELSE '' END
FROM study.Specimen InnerSpecimen
JOIN core.Containers ON InnerSpecimen.Container = core.Containers.EntityId
WHERE InnerSpecimen.RowId = study.Specimen.RowId)
GO

UPDATE study.SpecimenComment SET SpecimenHash =
    (SELECT SpecimenHash FROM study.Specimen
    WHERE study.SpecimenComment.Container = study.Specimen.Container AND
          study.SpecimenComment.GlobalUniqueId = study.Specimen.GlobalUniqueId)
GO

-- First, we rename 'specimen' to 'vial' to correct a long-standing bad name
ALTER TABLE study.Specimen
    DROP CONSTRAINT FK_SpecimenOrigin_Site
GO

DROP INDEX study.Specimen.IX_Specimen_AdditiveTypeId
DROP INDEX study.Specimen.IX_Specimen_DerivativeTypeId
DROP INDEX study.Specimen.IX_Specimen_OriginatingLocationId
DROP INDEX study.Specimen.IX_Specimen_PrimaryTypeId
DROP INDEX study.Specimen.IX_Specimen_Ptid
DROP INDEX study.Specimen.IX_Specimen_VisitValue
DROP INDEX study.Specimen.IX_Specimens_Derivatives2

-- First, we rename 'specimen' to 'vial' to correct a long-standing bad name
EXEC sp_rename 'study.Specimen', 'Vial'

EXEC sp_rename 'study.Vial.IX_Specimen_Container', 'IX_Vial_Container', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_CurrentLocation', 'IX_Vial_CurrentLocation', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_GlobalUniqueId', 'IX_Vial_GlobalUniqueId', 'INDEX'
EXEC sp_rename 'study.Vial.IX_Specimen_SpecimenHash', 'IX_Vial_Container_SpecimenHash', 'INDEX'


-- Next, we create the specimen table, which will hold static properties of a specimen draw (versus a vial)
CREATE TABLE study.Specimen
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SpecimenHash NVARCHAR(256),
    Ptid NVARCHAR(32),
    VisitDescription NVARCHAR(10),
    VisitValue NUMERIC(15,4),
    VolumeUnits NVARCHAR(20),
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20),
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
    CONSTRAINT PK_Specimen PRIMARY KEY (RowId)
)
GO

CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId)
CREATE INDEX IX_Specimen_Container ON study.Specimen(Container)
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId)
CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId)
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId)
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid)
CREATE INDEX IX_Specimen_Container_SpecimenHash ON study.Specimen(Container, SpecimenHash)
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue)
CREATE INDEX IX_Specimen_DerivativeTypeId2 ON study.Specimen(DerivativeTypeId2)
GO

-- we populate 'specimen' via a grouped query over the vial table to retrive the constant properties:
INSERT INTO study.Specimen (Container, SpecimenHash, Ptid, VisitDescription, VisitValue,
        VolumeUnits, PrimaryVolume, PrimaryVolumeUnits, PrimaryTypeId,
        AdditiveTypeId, DerivativeTypeId, DerivativeTypeId2, SubAdditiveDerivative,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OriginatingLocationId)
    SELECT Container, SpecimenHash, Ptid, VisitDescription, VisitValue,
        VolumeUnits, PrimaryVolume, PrimaryVolumeUnits, PrimaryTypeId,
        AdditiveTypeId, DerivativeTypeId, DerivativeTypeId2, SubAdditiveDerivative,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OriginatingLocationId
    FROM study.Vial
    GROUP BY Container, SpecimenHash, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        PrimaryVolume, PrimaryVolumeUnits, DerivativeTypeId2,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId
GO

-- after specimen is populated, we create a foreign key column on vial, populate it, and change the type to NOT NULL
ALTER TABLE study.Vial ADD SpecimenId INTEGER;
GO

UPDATE study.Vial SET SpecimenId = (
    SELECT RowId FROM study.Specimen
    WHERE study.Specimen.SpecimenHash = study.Vial.SpecimenHash AND
        study.Specimen.Container = study.Vial.Container
);

ALTER TABLE study.Vial ALTER COLUMN SpecimenId INTEGER NOT NULL
GO

CREATE INDEX IX_Vial_SpecimenId ON study.Vial(SpecimenId)
GO

ALTER TABLE study.Vial ADD CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
GO

ALTER TABLE study.Vial DROP
    COLUMN Ptid,
    COLUMN VisitDescription,
    COLUMN VisitValue,
    COLUMN VolumeUnits,
    COLUMN PrimaryTypeId,
    COLUMN AdditiveTypeId,
    COLUMN DerivativeTypeId,
    COLUMN PrimaryVolume,
    COLUMN PrimaryVolumeUnits,
    COLUMN DerivativeTypeId2,
    COLUMN DrawTimestamp,
    COLUMN SalReceiptDate,
    COLUMN ClassId,
    COLUMN ProtocolNumber,
    COLUMN SubAdditiveDerivative,
    COLUMN OriginatingLocationId
GO

-- Update the cached counts on the specimen table
UPDATE study.Specimen SET
    TotalVolume = VialCounts.TotalVolume,
    AvailableVolume = VialCounts.AvailableVolume,
    VialCount = VialCounts.VialCount,
    LockedInRequestCount = VialCounts.LockedInRequestCount,
    AtRepositoryCount = VialCounts.AtRepositoryCount,
    AvailableCount = VialCounts.AvailableCount,
    ExpectedAvailableCount = VialCounts.ExpectedAvailableCount
FROM (SELECT
    Container, SpecimenHash,
    SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN 1 THEN Volume ELSE 0 END) AS AvailableVolume,
        COUNT(GlobalUniqueId) AS VialCount,
        SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN 1 THEN 1 ELSE 0 END) AS AvailableCount,
        (COUNT(GlobalUniqueId) - SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) - SUM(CASE Requestable WHEN 0 THEN 1 ELSE 0 END)) AS ExpectedAvailableCount
    FROM study.Vial
    GROUP BY Container, SpecimenHash
    ) VialCounts
WHERE study.Specimen.Container = VialCounts.Container AND study.Specimen.SpecimenHash = VialCounts.SpecimenHash
GO

ALTER TABLE study.Vial DROP
    COLUMN FrozenTime,
    COLUMN ProcessingTime,
    COLUMN ProcessedByInitials,
    COLUMN ProcessingDate
GO

ALTER TABLE study.Vial ADD
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits NVARCHAR(20)
GO

UPDATE study.Vial SET
    PrimaryVolume = study.Specimen.PrimaryVolume,
    PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen
WHERE study.Specimen.RowId = study.Vial.SpecimenId
GO

ALTER TABLE study.Specimen DROP
    COLUMN PrimaryVolume,
    COLUMN PrimaryVolumeUnits
GO

EXEC sp_rename 'study.SpecimenEvent.SpecimenId', 'VialId', 'COLUMN'
GO

CREATE INDEX IDX_StudyData_ContainerKey ON study.StudyData(Container, _Key)
GO