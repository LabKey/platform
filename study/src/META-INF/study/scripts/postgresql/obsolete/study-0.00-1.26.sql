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
    SiteId INT NOT NULL,
    EntityId ENTITYID NOT NULL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Site PRIMARY KEY (Container,SiteId)
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


CREATE TABLE LDMSSample
(
    Container ENTITYID NOT NULL,
    RowId INT NOT NULL,
    Clasid VARCHAR(4), -- Group identifier; VTN, HPTN
    Lstudy FLOAT,	-- Protocol number; 35, 039.1
    Specid VARCHAR(12), -- LDMS generated specimen number; Unique per primary specimen. The default configuration in LDMS is to assign a unique specimen number for each additive and each derivative. Unique across labs.
    Guspec VARCHAR(11) NOT NULL, -- LDMS generated global unique specimen ID; Unique per aliquot. Unique within a lab and across labs.
    Txtpid INT, -- Participant Identifier
    Drawd TIMESTAMP, -- Date specimen was drawn
    Vidval FLOAT, -- Visit value; 1, 2.1
    Vidstr VARCHAR(3),	-- Visit description; Day, Mo, Scr, Wk, but typically Vst
    Recdt TIMESTAMP, -- Date that specimen was received at site-affiliated lab
    Primstr VARCHAR(3), -- Primary specimen type; BLD, CER, GLU, DWB
    Addstr VARCHAR(3), -- Additive tube type; HEP, NON, EDT
    Dervstr VARCHAR(3), -- Derivative type; DBS, PLA, SER
    Sec_tp VARCHAR(3), -- Sub additive/derivative
    Volume FLOAT, -- Aliquot volume value
    Volstr VARCHAR(3), -- Volume units; CEL, MG, ML, UG, N/A
    Condstr VARCHAR(3), -- Condition string; Usually SAT
    Sec_id VARCHAR(15), -- Other specimen ID; Stored as Other Spec ID on specimen management form in LDMS
    Addtim FLOAT, -- Expected time value for PK or metabolic samples; 1.0
    Addunt INT, 	-- Expected time unit for PK or metabolic samples; Hr
    CONSTRAINT PK_LDMSSample PRIMARY KEY (RowId),
    CONSTRAINT UQ_LDMSSample UNIQUE (Container, Guspec)
);


CREATE TABLE LDMSStorageType
(
    StorageTypeId INT NOT NULL,
    Label VARCHAR(100),
    CONSTRAINT PK_StorageType PRIMARY KEY (StorageTypeId)
);

INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (0, 'Not stored in LDMS');
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (2, 'Stored in LDMS');
INSERT INTO study.LDMSStorageType (StorageTypeId, Label) VALUES (-3, 'Deleted from LDMS');


CREATE TABLE LDMSShipFlag
(
    ShipFlagId INT NOT NULL,
    Label VARCHAR(100),
    CONSTRAINT PK_ShipFlag PRIMARY KEY (ShipFlagId)
);

INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (0, 'Not shipped via LDMS');
INSERT INTO study.LDMSShipFlag (ShipFlagId, Label) VALUES (1, 'Shipped via LDMS');


CREATE TABLE LDMSSampleEvent
(
	RowId SERIAL,
    LDMSSampleId INT NOT NULL, -- FK study.LDMSSample
    Container ENTITYID NOT NULL,
    Labid INT, -- LDMS lab number; 300 is JHU Central Lab
    Uspeci INT,	-- Unique specimen number; Used to link to base tables, unique at aliquot level. Unique within a lab, but can repeat across labs
    Parusp INT,	-- Parent unique specimen number; Unique per primary specimen within a lab, but can repeat across labs.
    Stored INT, -- Storage flag; 0=not stored in LDMS, 2=stored in LDMS, -3=permanently deleted from LDMS storage
    Stord TIMESTAMP, -- Date that specimen was stored in LDMS at each lab.
    Shipfg INT, -- Shipping flag; 0=not shipped via LDMS, 1=shipped via LDMS
    Shipno INT, -- LDMS generated shipping batch number; >0 for shipped batches
    Shipd TIMESTAMP, -- Date that specimen was shipped
    Rb_no INT, -- Imported batch number; >0 for imported batches. Does not match shipping batch number from other lab.
    Rlprot INT, -- Group/protocol Field
    Recvd TIMESTAMP, -- Date that specimen was received at subsequent lab. Should be equivalent to storage date at that lab.
    Commts VARCHAR(30), -- First 30 characters from comment field in specimen management,
    CONSTRAINT PK_LDMSSampleEvent PRIMARY KEY (RowId),
    CONSTRAINT FK_LDMSSampleEvent_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId),
    CONSTRAINT FK_LDMSSampleEvent_Site FOREIGN KEY (Container,Labid) REFERENCES study.Site(Container,SiteId),
    CONSTRAINT FK_LDMSSampleEvent_SampleStorageType FOREIGN KEY (Stored) REFERENCES study.LDMSStorageType(StorageTypeId),
    CONSTRAINT FK_LDMSSampleEvent_SampleShipFlag FOREIGN KEY (Shipfg) REFERENCES study.LDMSShipFlag(ShipFlagId)
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
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (Container,DestinationSiteId) REFERENCES study.Site(Container,SiteId)
);


CREATE TABLE SampleRequestLDMSSample
(
    RowId SERIAL,
    Container ENTITYID NOT NULL DEFAULT '',
    SampleRequestId INT NOT NULL,
    LDMSSampleId INT NOT NULL,

    CONSTRAINT PK_SampleRequestLDMSSample PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestLDMSSample_LDMSSample FOREIGN KEY (LDMSSampleId) REFERENCES study.LDMSSample(RowId)
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
    CONSTRAINT FK_SampleRequestRequirement_Site FOREIGN KEY (Container,SiteId) REFERENCES study.Site(Container,SiteId)
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
