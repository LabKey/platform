/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

IF NOT EXISTS (SELECT * FROM sysusers WHERE name ='study')
    EXEC sp_addapprole 'study', 'password'
GO

IF NOT EXISTS (select * from systypes where name ='LSIDtype')
    EXEC sp_addtype 'LSIDtype', 'nvarchar(300)'
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
    VisitId INT NOT NULL,	-- FK
    DataSetId INT NOT NULL,	-- FK
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


CREATE TABLE study.SpecimenAdditive (
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

CREATE TABLE study.SpecimenDerivative (
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

CREATE TABLE study.SpecimenPrimaryType (
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

CREATE TABLE study.Specimen (
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

CREATE TABLE study.SpecimenEvent (
    RowId INT IDENTITY(1,1),
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
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenId INT NOT NULL,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestSpecimen_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
)
GO
