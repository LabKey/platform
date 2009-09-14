/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

SET search_path TO study, public;

CREATE TABLE Study
    (
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Study PRIMARY KEY (Container)
    );

CREATE TABLE Site
    (
    RowId SERIAL,
    EntityId ENTITYID NOT NULL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    ScharpId INT,
    LdmsLabCode INT,
    LabwareLabCode VARCHAR(20),
    LabUploadCode VARCHAR(2),
    IsSal Boolean,
    IsRepository Boolean,
    IsEndpoint Boolean,
    CONSTRAINT PK_Site PRIMARY KEY (RowId)
    );


CREATE TABLE Visit
    (
    VisitId INT NOT NULL,
    Label VARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    Container ENTITYID NOT NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    CONSTRAINT PK_Visit PRIMARY KEY (Container,VisitId)
    );

CREATE TABLE VisitMap
    (
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,	-- FK
    DataSetId INT NOT NULL,	-- FK
    IsRequired BOOLEAN NOT NULL DEFAULT '1',
    CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitId,DataSetId)
    );

CREATE TABLE DataSet -- AKA CRF or Assay
   (
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI VARCHAR(200) NULL,
    Label VARCHAR(200) NULL,
    Category VARCHAR(200) NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    CONSTRAINT PK_DataSet PRIMARY KEY (Container,DataSetId)
   );

CREATE TABLE SampleRequestStatus
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),
    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
);


CREATE TABLE SampleRequestActor
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),
    PerSite Boolean NOT NULL DEFAULT '0',
    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
);


CREATE TABLE SampleRequest
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
    Hidden Boolean NOT NULL DEFAULT '0',

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
);


CREATE TABLE SampleRequestRequirement
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    RequestId INT NOT NULL,
    ActorId INT NOT NULL,
    SiteId INT NULL,
    Description VARCHAR(300),
    Complete BOOLEAN NOT NULL DEFAULT '0',

    CONSTRAINT PK_SampleRequestRequirement PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestRequirement_SampleRequestActor FOREIGN KEY (ActorId) REFERENCES study.SampleRequestActor(RowId),
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (SiteId) REFERENCES study.Site(RowId)
);


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
    
    CONSTRAINT FK_SampleRequestEvent_SampleRequest FOREIGN KEY (RequestId) REFERENCES study.SampleRequest(RowId)
);


CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(16) NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);


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

    CONSTRAINT PK_Report PRIMARY KEY (ReportId),
    CONSTRAINT UQ_Report UNIQUE (ContainerId, Label)
);


CREATE TABLE study.StudyData
(
	Container ENTITYID NOT NULL,
	ParticipantId VARCHAR(32) NOT NULL,
	VisitId INT4 NULL,
	DatasetId INT4 NOT NULL,
	LSID VARCHAR(200) NOT NULL,
	CONSTRAINT PK_StudyData PRIMARY KEY (LSID),
	CONSTRAINT AK_StudyData UNIQUE (Container, DatasetId, VisitId, ParticipantId)
);
CLUSTER AK_StudyData ON study.StudyData;

-- consider container,participant,dataset,visit index

CREATE TABLE study.SpecimenAdditive (
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsAdditiveCode VARCHAR(3),
    LabwareAdditiveCode VARCHAR(20),
    Additive VARCHAR(100),
    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container)
);

CREATE TABLE study.SpecimenDerivative (
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsDerivativeCode VARCHAR(3),
    LabwareDerivativeCode VARCHAR(20),
    Derivative VARCHAR(100),
    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container)
);

CREATE TABLE study.SpecimenPrimaryType (
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    PrimaryTypeLdmsCode VARCHAR(5),
    PrimaryTypeLabwareCode VARCHAR(5),
    PrimaryType VARCHAR(100),
    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container)
);

CREATE TABLE study.Specimen (
    RowId INT NOT NULL, -- FK exp.Material
    Container ENTITYID NOT NULL,
    RecordSource VARCHAR(10),
    GlobalUniqueId VARCHAR(20) NOT NULL,
    Ptid INT,
    DrawTimestamp TIMESTAMP,
    SalReceiptDate TIMESTAMP,
    SpecimenNumber VARCHAR(50),
    ClassId VARCHAR(4),
    VisitValue FLOAT,
    ProtocolNumber VARCHAR(10),
    VisitDescription VARCHAR(3),
    OtherSpecimenId VARCHAR(20),
    Volume FLOAT,
    VolumeUnits VARCHAR(3),
    ExpectedTimeValue FLOAT,
    ExpectedTimeUnit VARCHAR(15),
    GroupProtocol INT,
    SubAdditiveDerivative VARCHAR(20),
    PrimaryTypeId INT,
    DerivativeTypeId INT,
    AdditiveTypeId INT,
    SpecimenCondition VARCHAR(3),
    SampleNumber INT,
    XSampleOrigin VARCHAR(20),
    ExternalLocation VARCHAR(20),
    UpdateTimestamp TIMESTAMP,
    CONSTRAINT PK_Specimens PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_GlobalId UNIQUE (GlobalUniqueId, Container),
    CONSTRAINT FK_Specimens_Additives FOREIGN KEY (AdditiveTypeId) REFERENCES study.SpecimenAdditive(RowId),
    CONSTRAINT FK_Specimens_Derivatives FOREIGN KEY (DerivativeTypeId) REFERENCES study.SpecimenDerivative(RowId),
    CONSTRAINT FK_Specimens_PrimaryTypes FOREIGN KEY (PrimaryTypeId) REFERENCES study.SpecimenPrimaryType(RowId)
);

CREATE TABLE study.SpecimenEvent (
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT,
    SpecimenId INT NOT NULL,
    LabId INT,
    UniqueSpecimenId VARCHAR(20),
    ParentSpecimenId INT,
    Stored INT,
    StorageFlag INT,
    StorageDate TIMESTAMP,
    ShipFlag INT,
    ShipBatchNumber INT,
    ShipDate TIMESTAMP,
    ImportedBatchNumber INT,
    LabReceiptDate TIMESTAMP,
    Comments VARCHAR(30),
    CONSTRAINT PK_SpecimensEvents PRIMARY KEY (RowId),
    CONSTRAINT UQ_Specimens_ScharpId UNIQUE (ScharpId, Container),
    CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId),
    CONSTRAINT FK_Specimens_Site FOREIGN KEY (LabId) REFERENCES study.Site(RowId)
);

CREATE TABLE study.SampleRequestSpecimen
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenId INT NOT NULL,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestSpecimen_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
);