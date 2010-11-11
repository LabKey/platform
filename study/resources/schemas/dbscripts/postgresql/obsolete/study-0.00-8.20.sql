/*
 * Copyright (c) 2010 LabKey Corporation
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
 
/* study-0.00-2.30.sql */

/* study-0.00-2.00.sql */

/* study-0.00-1.30.sql */

CREATE SCHEMA study;

CREATE TABLE study.Study
(
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_Study PRIMARY KEY (Container)
);

CREATE TABLE study.Site
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


CREATE TABLE study.Visit
(
    VisitId INT NOT NULL,
    Label VARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    Container ENTITYID NOT NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,

    CONSTRAINT PK_Visit PRIMARY KEY (Container,VisitId)
);

CREATE TABLE study.VisitMap
(
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,   -- FK
    DataSetId INT NOT NULL, -- FK
    IsRequired BOOLEAN NOT NULL DEFAULT '1',

    CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitId,DataSetId)
);

CREATE TABLE study.DataSet -- AKA CRF or Assay
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

CREATE TABLE study.SampleRequestStatus
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),

    CONSTRAINT PK_SampleRequestStatus PRIMARY KEY (RowId)
);


CREATE TABLE study.SampleRequestActor
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SortOrder INT NULL,
    Label VARCHAR(100),
    PerSite Boolean NOT NULL DEFAULT '0',

    CONSTRAINT PK_SampleRequestActor PRIMARY KEY (RowId)
);


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
    Hidden Boolean NOT NULL DEFAULT '0',

    CONSTRAINT PK_SampleRequest PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequest_SampleRequestStatus FOREIGN KEY (StatusId) REFERENCES study.SampleRequestStatus(RowId),
    CONSTRAINT FK_SampleRequest_Site FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId)
);


CREATE TABLE study.SampleRequestRequirement
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

CREATE TABLE study.SpecimenAdditive
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsAdditiveCode VARCHAR(3),
    LabwareAdditiveCode VARCHAR(20),
    Additive VARCHAR(100),

    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container)
);

CREATE TABLE study.SpecimenDerivative
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsDerivativeCode VARCHAR(3),
    LabwareDerivativeCode VARCHAR(20),
    Derivative VARCHAR(100),

    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container)
);

CREATE TABLE study.SpecimenPrimaryType
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    PrimaryTypeLdmsCode VARCHAR(5),
    PrimaryTypeLabwareCode VARCHAR(5),
    PrimaryType VARCHAR(100),

    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container)
);

CREATE TABLE study.Specimen
(
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

CREATE TABLE study.SpecimenEvent
(
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

/* study-1.30-1.40.sql */

ALTER TABLE study.StudyData ADD COLUMN Created TIMESTAMP NULL, ADD COLUMN Modified TIMESTAMP NULL, ADD COLUMN VisitDate TIMESTAMP NULL;
ALTER TABLE study.DataSet ADD COLUMN EntityId ENTITYID;
ALTER TABLE study.Study ADD COLUMN EntityId ENTITYID;

CREATE TABLE study.AssayRun
(
    RowId SERIAL,
    AssayType VARCHAR(200) NOT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_AssayRun PRIMARY KEY (RowId)
);

/* study-1.40-1.50.sql */

ALTER TABLE study.SampleRequestStatus
    ADD FinalState Boolean NOT NULL DEFAULT '0',
    ADD SpecimensLocked Boolean NOT NULL DEFAULT '1';
    
ALTER TABLE study.Specimen
    ALTER COLUMN Ptid TYPE VARCHAR(32);

ALTER TABLE study.Participant
    ADD COLUMN EnrollmentSiteId INT NULL,
    ADD COLUMN CurrentSiteId INT NULL,
    ADD CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
    ADD CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId);

ALTER TABLE study.SpecimenEvent
    DROP CONSTRAINT UQ_Specimens_ScharpId;

/* study-1.50-1.60.sql */

-- Change visit ids to type numeric:
ALTER TABLE study.Visit
    DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.Visit
    ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,VisitId);

ALTER TABLE study.VisitMap
    DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitId,DataSetId);

ALTER TABLE study.studydata
    DROP CONSTRAINT AK_StudyData;
ALTER TABLE study.studydata
    ALTER COLUMN VisitId TYPE NUMERIC(15,4);
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_StudyData UNIQUE (Container, DatasetId, VisitId, ParticipantId);

ALTER TABLE study.Specimen
    ALTER COLUMN VisitValue TYPE NUMERIC(15,4);

CREATE TABLE study.UploadLog
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy USERID NOT NULL,
    Description TEXT,
    FilePath VARCHAR(512),
    DatasetId INT NOT NULL,
    Status VARCHAR(20),
    CONSTRAINT PK_UploadLog PRIMARY KEY (RowId),
    CONSTRAINT UQ_UploadLog_FilePath UNIQUE (FilePath)
);

ALTER TABLE study.Report
    ADD COLUMN ShowWithDataset INT NULL;

/* study-1.60-1.70.sql */

ALTER TABLE study.Visit ADD COLUMN VisitDateDatasetId INT;

ALTER TABLE study.DataSet ADD COLUMN VisitDatePropertyName VARCHAR(200);

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
    CONSTRAINT PK_Plate PRIMARY KEY (RowId)
);


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

--
-- refactor Visit, split VisitId into two different keys
--
-- VisitId --> VisitSequenceId
-- VisitId --> VisitRowId
--

-- in with the new
ALTER TABLE study.Visit ADD COLUMN RowId SERIAL;
ALTER TABLE study.Visit DROP CONSTRAINT PK_Visit;
ALTER TABLE study.Visit ADD CONSTRAINT PK_Visit PRIMARY KEY (Container,RowId);
ALTER TABLE study.Visit ADD COLUMN SequenceNumMin NUMERIC(15,4) NOT NULL DEFAULT 0;
ALTER TABLE study.Visit ADD COLUMN SequenceNumMax NUMERIC(15,4) NOT NULL DEFAULT 0;

--
-- fix up VisitMap
--

ALTER TABLE study.VisitMap DROP CONSTRAINT PK_VisitMap;
ALTER TABLE study.VisitMap ADD COLUMN VisitRowId INT4;
ALTER TABLE study.VisitMap DROP COLUMN VisitId;

ALTER TABLE study.VisitMap
    ADD CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitRowId,DataSetId);

--
-- fix up ParticipantVisit
--

CREATE TABLE study.ParticipantVisit
(
    Container ENTITYID NOT NULL,
    ParticipantId VARCHAR(32) NOT NULL,
    VisitRowId int NULL,
    SequenceNum NUMERIC(15,4) NOT NULL,
    VisitDate TIMESTAMP
);
ALTER TABLE study.ParticipantVisit ADD CONSTRAINT PK_ParticipantVisit PRIMARY KEY (Container, SequenceNum, ParticipantId);


--
-- refactor StudyData
--

ALTER TABLE study.StudyData ADD COLUMN SequenceNum Numeric(15,4);
--ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;
ALTER TABLE study.StudyData DROP COLUMN VisitId;
ALTER TABLE study.studydata
    ADD CONSTRAINT AK_ParticipantDataset UNIQUE (Container, DatasetId, SequenceNum, ParticipantId);

-- out with the old
ALTER TABLE study.Visit DROP COLUMN VisitId;

ALTER TABLE study.StudyData
    ADD COLUMN SourceLSID VARCHAR(200) NULL;

ALTER TABLE study.DataSet ADD COLUMN KeyPropertyName VARCHAR(50);             -- Property name in TypeURI

ALTER TABLE study.StudyData ADD COLUMN _key VARCHAR(200) NULL;          -- assay key column, used only on INSERT for UQ index

ALTER TABLE study.StudyData DROP CONSTRAINT AK_ParticipantDataset;

ALTER TABLE study.studydata
    ADD CONSTRAINT UQ_StudyData UNIQUE (Container, DatasetId, SequenceNum, ParticipantId, _key);

-- rename VisitDate -> _VisitDate to avoid some confusion

ALTER TABLE study.StudyData ADD _VisitDate TIMESTAMP NULL;
ALTER TABLE study.StudyData DROP COLUMN VisitDate;

/* study-1.70-2.00.sql */

ALTER TABLE study.SampleRequestSpecimen
ADD COLUMN SpecimenGlobalUniqueId VARCHAR(100);

ALTER TABLE study.Specimen
    DROP COLUMN SpecimenCondition,
    DROP COLUMN SampleNumber,
    DROP COLUMN XSampleOrigin,
    DROP COLUMN ExternalLocation,
    DROP COLUMN UpdateTimestamp,
    DROP COLUMN OtherSpecimenId,
    DROP COLUMN ExpectedTimeUnit,
    DROP COLUMN RecordSource,
    DROP COLUMN GroupProtocol,
    DROP COLUMN ExpectedTimeValue;

ALTER TABLE study.SpecimenEvent
    ADD COLUMN SpecimenCondition VARCHAR(3),
    ADD COLUMN SampleNumber INT,
    ADD COLUMN XSampleOrigin VARCHAR(20),
    ADD COLUMN ExternalLocation VARCHAR(20),
    ADD COLUMN UpdateTimestamp TIMESTAMP,
    ADD COLUMN OtherSpecimenId VARCHAR(20),
    ADD COLUMN ExpectedTimeUnit VARCHAR(15),
    ADD COLUMN ExpectedTimeValue FLOAT,
    ADD COLUMN GroupProtocol INT,
    ADD COLUMN RecordSource VARCHAR(10);

-- fix up study.SampleRequestSpecimen
ALTER TABLE study.SampleRequestSpecimen DROP CONSTRAINT fk_SampleRequestSpecimen_specimen;
ALTER TABLE study.SampleRequestSpecimen DROP COLUMN SpecimenId;

ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Additives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_PrimaryTypes;

ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
CREATE INDEX IX_SpecimenAdditive_ScharpId ON study.SpecimenAdditive(ScharpId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
CREATE INDEX IX_SpecimenDerivative_ScharpId ON study.SpecimenDerivative(ScharpId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
CREATE INDEX IX_SpecimenPrimaryType_ScharpId ON study.SpecimenPrimaryType(ScharpId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container);

CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(SpecimenId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);

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
    Draft Boolean NOT NULL DEFAULT '1',
    Label VARCHAR(200) NOT NULL,
    Description TEXT,
    XML TEXT,

    CONSTRAINT PK_StudyDesignVersion PRIMARY KEY (StudyId,Revision),
    CONSTRAINT FK_StudyDesignVersion_StudyDesign FOREIGN KEY (StudyId) REFERENCES study.StudyDesign(StudyId),
    CONSTRAINT UQ_StudyDesignVersion UNIQUE (Container,Label,Revision)
);

ALTER TABLE study.Report DROP CONSTRAINT PK_Report;

ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId);

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

/*
 * Fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3111
 */

/* study-2.00-2.10.sql */

-- VISITMAP

ALTER TABLE study.visitmap RENAME COLUMN isrequired TO required;


-- DATASET

ALTER TABLE study.dataset ADD COLUMN name VARCHAR(200);
ALTER TABLE study.dataset ALTER name SET NOT NULL;
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);

ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

ALTER TABLE study.Site
    ADD IsClinic Boolean;

ALTER TABLE study.Specimen
    ADD OriginatingLocationId INT,
    ADD CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId);

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);

ALTER TABLE study.StudyDesign ADD COLUMN StudyEntityId entityid;

ALTER TABLE study.Dataset ADD COLUMN Description TEXT NULL;

--ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
--ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
--  (
--      Container,
--      DatasetId,
--      SequenceNum,
--      ParticipantId,
--      _key
--  )

ALTER TABLE study.StudyDesign
    ADD Active boolean NOT NULL DEFAULT FALSE;

ALTER TABLE study.StudyDesign
    DROP StudyEntityId,
    ADD SourceContainer ENTITYID;

/* study-2.10-2.20.sql */

ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL;

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL,
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);

DROP TABLE study.AssayRun;

ALTER TABLE study.Specimen
    ADD Requestable Boolean;

ALTER TABLE study.SpecimenEvent
    ADD freezer VARCHAR(200),
    ADD fr_level1 VARCHAR(200),
    ADD fr_level2 VARCHAR(200),
    ADD fr_container VARCHAR(200),
    ADD fr_position VARCHAR(200);

/* study-2.20-2.30.sql */

ALTER TABLE study.Study
    ADD DateBased Boolean DEFAULT false,
    ADD StartDate TIMESTAMP;

ALTER TABLE study.ParticipantVisit
    ADD Day int4;

ALTER TABLE study.Participant
    ADD StartDate TIMESTAMP;

ALTER TABLE study.Dataset
    ADD DemographicData Boolean DEFAULT false;

ALTER TABLE study.Study ADD StudySecurity Boolean DEFAULT false;

/* study-2.30-8.10.sql */

/* study-2.30-2.31.sql */

ALTER TABLE study.Plate ADD COLUMN Type VARCHAR(200);

/* study-2.31-2.32.sql */

CREATE TABLE study.Cohort
(
    RowId SERIAL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Cohort PRIMARY KEY (RowId),
    CONSTRAINT UQ_Cohort_Label UNIQUE(Label, Container)
);

ALTER TABLE study.Dataset
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Dataset_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Dataset_CohortId ON study.Dataset(CohortId);

ALTER TABLE study.Participant
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Participant_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Participant_CohortId ON study.Participant(CohortId);
CREATE INDEX IX_Participant_ParticipantId ON study.Participant(ParticipantId);
CREATE INDEX IX_Specimen_Ptid ON study.Specimen(Ptid);

ALTER TABLE study.Visit
    ADD COLUMN CohortId INT NULL,
    ADD CONSTRAINT FK_Visit_Cohort FOREIGN KEY (CohortId) REFERENCES study.Cohort (RowId);

CREATE INDEX IX_Visit_CohortId ON study.Visit(CohortId);

ALTER TABLE study.Study
    ADD COLUMN ParticipantCohortDataSetId INT NULL,
    ADD COLUMN ParticipantCohortProperty VARCHAR(200) NULL;

/* study-2.32-2.33.sql */

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId TYPE VARCHAR(32);

/* study-2.33-2.34.sql */

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments TYPE VARCHAR(200);

/* study-8.10-8.20.sql */

/* study-8.11-8.12.sql */

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

/* study-8.12-8.13.sql */

ALTER TABLE study.specimen
    ADD column CurrentLocation INT,
    ADD CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId);

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);

/* study-8.13-8.14.sql */

ALTER TABLE study.Dataset
    ADD COLUMN KeyPropertyManaged BOOLEAN NOT NULL DEFAULT FALSE;

/* study-8.14-8.15.sql */

ALTER TABLE study.Study
    ADD COLUMN DatasetRowsEditable BOOLEAN NOT NULL DEFAULT FALSE;
