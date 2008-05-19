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

-- Tables and views used for Study module

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
    SiteId INT NOT NULL,
    EntityId ENTITYID NOT NULL DEFAULT NEWID(),
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Site PRIMARY KEY CLUSTERED (Container,SiteId)
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
go


CREATE TABLE study.LDMSSample
(
    Container ENTITYID NOT NULL,
    RowId INT NOT NULL, -- PK, FK exp.Material
    Clasid NVARCHAR(4), -- Group identifier; VTN, HPTN
    Lstudy FLOAT,	-- Protocol number; 35, 039.1
    Specid NVARCHAR(12), -- LDMS generated specimen number; Unique per primary specimen. The default configuration in LDMS is to assign a unique specimen number for each additive and each derivative. Unique across labs.
    Guspec NVARCHAR(11) NOT NULL, -- LDMS generated global unique specimen ID; Unique per aliquot. Unique within a lab and across labs.
    Txtpid INT, -- Participant Identifier
    Drawd DATETIME, -- Date specimen was drawn
    Vidval FLOAT, -- Visit value; 1, 2.1
    Vidstr NVARCHAR(3),	-- Visit description; Day, Mo, Scr, Wk, but typically Vst
    Recdt DATETIME, -- Date that specimen was received at site-affiliated lab
    Primstr NVARCHAR(3), -- Primary specimen type; BLD, CER, GLU, DWB
    Addstr NVARCHAR(3), -- Additive tube type; HEP, NON, EDT
    Dervstr NVARCHAR(3), -- Derivative type; DBS, PLA, SER
    Sec_tp NVARCHAR(3), -- Sub additive/derivative
    Volume FLOAT, -- Aliquot volume value
    Volstr NVARCHAR(3), -- Volume units; CEL, MG, ML, UG, N/A
    Condstr NVARCHAR(3), -- Condition string; Usually SAT
    Sec_id NVARCHAR(15), -- Other specimen ID; Stored as Other Spec ID on specimen management form in LDMS
    Addtim FLOAT, -- Expected time value for PK or metabolic samples; 1.0
    Addunt INT, 	-- Expected time unit for PK or metabolic samples; Hr
    CONSTRAINT PK_LDMSSample PRIMARY KEY (RowId),
    CONSTRAINT UQ_LDMSSample UNIQUE (Container, Guspec),
)
go


CREATE TABLE study.LDMSStorageType
(
    StorageTypeId INT NOT NULL,
    Label NVARCHAR(100),
    CONSTRAINT PK_StorageType PRIMARY KEY (StorageTypeId)
)
go

INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (0, 'Not stored in LDMS')
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (2, 'Stored in LDMS')
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (-3, 'Deleted from LDMS')
go


CREATE TABLE study.LDMSShipFlag
(
    ShipFlagId INT NOT NULL,
    Label NVARCHAR(100),
    CONSTRAINT PK_ShipFlag PRIMARY KEY (ShipFlagId)
)
go


INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (0, 'Not shipped via LDMS')
INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (1, 'Shipped via LDMS')
go


CREATE TABLE study.LDMSSampleEvent
(
	RowId INT IDENTITY(1,1),
    LDMSSampleId INT NOT NULL, -- FK study.LDMSSample
    Container ENTITYID NOT NULL,
    Labid INT, -- LDMS lab number; 300 is JHU Central Lab
    Uspeci INT,	-- Unique specimen number; Used to link to base tables, unique at aliquot level. Unique within a lab, but can repeat across labs
    Parusp INT,	-- Parent unique specimen number; Unique per primary specimen within a lab, but can repeat across labs.
    Stored INT, -- Storage flag; 0=not stored in LDMS, 2=stored in LDMS, -3=permanently deleted from LDMS storage
    Stord DATETIME, -- Date that specimen was stored in LDMS at each lab.
    Shipfg INT, -- Shipping flag; 0=not shipped via LDMS, 1=shipped via LDMS
    Shipno INT, -- LDMS generated shipping batch number; >0 for shipped batches
    Shipd DATETIME, -- Date that specimen was shipped
    Rb_no INT, -- Imported batch number; >0 for imported batches. Does not match shipping batch number from other lab.
    Rlprot INT, -- Group/protocol Field
    Recvd DATETIME, -- Date that specimen was received at subsequent lab. Should be equivalent to storage date at that lab.
    Commts NVARCHAR(30), -- First 30 characters from comment field in specimen management
    CONSTRAINT FK_LDMSSampleEvent_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId),
    CONSTRAINT FK_LDMSSampleEvent_Site FOREIGN KEY (Container,Labid) REFERENCES study.Site(Container,SiteId),
    CONSTRAINT FK_LDMSSampleEvent_SampleStorageType FOREIGN KEY (Stored) REFERENCES study.LDMSStorageType(StorageTypeId),
    CONSTRAINT FK_LDMSSampleEvent_SampleShipFlag FOREIGN KEY (Shipfg) REFERENCES study.LDMSShipFlag(ShipFlagId),
   	CONSTRAINT PK_LDMSSampleEvent PRIMARY KEY (RowId)
)
go


CREATE TABLE study.SampleRequestStatus
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label NVARCHAR(100),
    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
)
go


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
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (Container,DestinationSiteId) REFERENCES study.Site(Container,SiteId)
)
go


CREATE TABLE study.SampleRequestLDMSSample
(
    RowId INT IDENTITY(1,1),
    Container ENTITYID DEFAULT '',

    SampleRequestId INT NOT NULL,
    LDMSSampleId INT NOT NULL,

    CONSTRAINT PK_SampleRequestLDMSSample PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId)
)
go
    

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
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (Container,SiteId) REFERENCES study.Site(Container,SiteId)
)
go


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
go


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
);
go


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
go
