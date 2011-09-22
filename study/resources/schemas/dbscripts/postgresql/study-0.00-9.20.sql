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
    VisitRowId INT NULL,
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

ALTER TABLE study.StudyDesign ADD COLUMN StudyEntityId ENTITYID;

ALTER TABLE study.Dataset ADD COLUMN Description TEXT NULL;

ALTER TABLE study.StudyDesign
    ADD Active boolean NOT NULL DEFAULT FALSE;

ALTER TABLE study.StudyDesign
    DROP StudyEntityId,
    ADD SourceContainer ENTITYID;

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

ALTER TABLE study.Plate ADD COLUMN Type VARCHAR(200);

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

ALTER TABLE study.Participant
    ALTER COLUMN ParticipantId TYPE VARCHAR(32);

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN Comments TYPE VARCHAR(200);

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

ALTER TABLE study.specimen
    ADD COLUMN CurrentLocation INT,
    ADD CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId);

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);

ALTER TABLE study.Dataset
    ADD COLUMN KeyPropertyManaged BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE study.Study
    ADD COLUMN DatasetRowsEditable BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE study.Study
    ADD COLUMN SecurityType VARCHAR(32);

UPDATE study.Study
    SET SecurityType = 'ADVANCED'
    WHERE
    StudySecurity = TRUE;

UPDATE study.Study
    SET SecurityType = 'EDITABLE_DATASETS'
    WHERE
    StudySecurity = FALSE AND
    DatasetRowsEditable = TRUE;

UPDATE study.Study
    SET SecurityType = 'BASIC'
    WHERE
    StudySecurity = FALSE AND
    DatasetRowsEditable = FALSE;

ALTER TABLE study.Study
    DROP COLUMN StudySecurity;

ALTER TABLE study.Study
    DROP COLUMN DatasetRowsEditable;

ALTER TABLE study.Study
    ALTER COLUMN SecurityType SET NOT NULL;

ALTER TABLE study.Cohort
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Cohort
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Cohort
    ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Visit
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Visit
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Visit
    ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Study
    ADD COLUMN LSID VARCHAR(200);

UPDATE study.Study
    SET LSID='UPGRADE_REQUIRED'
    WHERE LSID IS NULL;

ALTER TABLE study.Study
    ALTER COLUMN LSID SET NOT NULL;

ALTER TABLE study.Study
    ADD COLUMN manualCohortAssignment boolean NOT NULL DEFAULT FALSE;

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

ALTER TABLE study.StudyData
    ADD COLUMN QCState INT NULL,
    ADD CONSTRAINT FK_StudyData_QCState FOREIGN KEY (QCState) REFERENCES study.QCState (RowId);

CREATE INDEX IX_StudyData_QCState ON study.StudyData(QCState);

ALTER TABLE study.Study
    ADD DefaultPipelineQCState INT,
    ADD DefaultAssayQCState INT,
    ADD DefaultDirectEntryQCState INT,
    ADD ShowPrivateDataByDefault BOOLEAN NOT NULL DEFAULT False,
    ADD CONSTRAINT FK_Study_DefaultPipelineQCState FOREIGN KEY (DefaultPipelineQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultAssayQCState FOREIGN KEY (DefaultAssayQCState) REFERENCES study.QCState (RowId),
    ADD CONSTRAINT FK_Study_DefaultDirectEntryQCState FOREIGN KEY (DefaultDirectEntryQCState) REFERENCES study.QCState (RowId);

ALTER TABLE study.Visit
    DROP COLUMN LSID;

UPDATE study.Study
    SET SecurityType = 'BASIC_READ'
    WHERE
    SecurityType = 'BASIC';

UPDATE study.Study
    SET SecurityType = 'BASIC_WRITE'
    WHERE
    SecurityType = 'EDITABLE_DATASETS';

UPDATE study.Study
    SET SecurityType = 'ADVANCED_READ'
    WHERE
    SecurityType = 'ADVANCED';

CREATE TABLE study.SpecimenComment
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SpecimenNumber VARCHAR(50) NOT NULL,
    GlobalUniqueId VARCHAR(50) NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP,
    ModifiedBy USERID,
    Modified TIMESTAMP,
    Comment TEXT,
    CONSTRAINT PK_SpecimenComment PRIMARY KEY (RowId)
);

CREATE INDEX IX_SpecimenComment_GlobalUniqueId ON study.SpecimenComment(GlobalUniqueId);
CREATE INDEX IX_SpecimenComment_SpecimenNumber ON study.SpecimenComment(SpecimenNumber);

UPDATE study.dataset
    SET label = name WHERE label IS NULL;

ALTER TABLE study.dataset
    ALTER COLUMN label SET NOT NULL;

ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetLabel UNIQUE (container, label);

ALTER TABLE study.Specimen
    ADD COLUMN SpecimenHash VARCHAR(256);

DROP INDEX study.IX_SpecimenComment_SpecimenNumber;

ALTER TABLE study.SpecimenComment
    ADD COLUMN SpecimenHash VARCHAR(256),
    DROP COLUMN SpecimenNumber;

CREATE INDEX IX_SpecimenComment_SpecimenHash ON study.SpecimenComment(Container, SpecimenHash);
CREATE INDEX IX_Specimen_SpecimenHash ON study.Specimen(Container, SpecimenHash);

/* study-8.30-9.10.sql */

ALTER TABLE study.Dataset
    ADD COLUMN protocolid INT NULL;

ALTER TABLE study.SpecimenEvent
    ADD COLUMN SpecimenNumber VARCHAR(50);

UPDATE study.SpecimenEvent SET SpecimenNumber =
    (SELECT SpecimenNumber FROM study.Specimen WHERE study.SpecimenEvent.SpecimenId = study.Specimen.RowId);

ALTER TABLE study.Specimen
    DROP COLUMN SpecimenNumber;

SELECT core.fn_dropifexists('Visit', 'study', 'INDEX', 'IX_Visit_ContainerSeqNum');
SELECT core.fn_dropifexists('Visit', 'study', 'INDEX', 'IX_Visit_SequenceNumMin');
ALTER TABLE study.Visit ADD CONSTRAINT UQ_Visit_ContSeqNum UNIQUE(Container, SequenceNumMin);

UPDATE exp.protocol SET MaxInputMaterialPerInstance = NULL
    WHERE
        (
            Lsid LIKE '%:LuminexAssayProtocol.Folder-%' OR
            Lsid LIKE '%:GeneralAssayProtocol.Folder-%' OR
            Lsid LIKE '%:NabAssayProtocol.Folder-%' OR
            Lsid LIKE '%:ElispotAssayProtocol.Folder-%' OR
            Lsid LIKE '%:MicroarrayAssayProtocol.Folder-%'
        ) AND ApplicationType = 'ExperimentRun'
;

-- Remove ScharpId from SpecimenPrimaryType
ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
DROP INDEX study.IX_SpecimenPrimaryType_ScharpId;
ALTER TABLE study.SpecimenPrimaryType ADD COLUMN ExternalId INT NOT NULL DEFAULT 0;
UPDATE study.SpecimenPrimaryType SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenPrimaryType DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenPrimaryType_ExternalId ON study.SpecimenPrimaryType(ExternalId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryType UNIQUE (ExternalId, Container);

-- Remove ScharpId from SpecimenDerivativeType
ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
DROP INDEX study.IX_SpecimenDerivative_ScharpId;
ALTER TABLE study.SpecimenDerivative ADD COLUMN ExternalId INT NOT NULL DEFAULT 0;
UPDATE study.SpecimenDerivative SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenDerivative DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenDerivative_ExternalId ON study.SpecimenDerivative(ExternalId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivative UNIQUE (ExternalId, Container);

-- Remove ScharpId from SpecimenAdditive
ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
DROP INDEX study.IX_SpecimenAdditive_ScharpId;
ALTER TABLE study.SpecimenAdditive ADD COLUMN ExternalId INT NOT NULL DEFAULT 0;
UPDATE study.SpecimenAdditive SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenAdditive DROP COLUMN ScharpId;
CREATE INDEX IX_SpecimenAdditive_ExternalId ON study.SpecimenAdditive(ExternalId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additive UNIQUE (ExternalId, Container);

-- Remove ScharpId from Site
ALTER TABLE study.Site ADD COLUMN ExternalId INT;
UPDATE study.Site SET ExternalId = ScharpId;
ALTER TABLE study.Site DROP COLUMN ScharpId;

-- Remove ScharpId from SpecimenEvent
ALTER TABLE study.SpecimenEvent ADD COLUMN ExternalId INT;
UPDATE study.SpecimenEvent SET ExternalId = ScharpId;
ALTER TABLE study.SpecimenEvent DROP COLUMN ScharpId;

UPDATE study.Specimen SET
    PrimaryTypeId = (SELECT RowId FROM study.SpecimenPrimaryType WHERE
        ExternalId = study.Specimen.PrimaryTypeId AND study.SpecimenPrimaryType.Container = study.Specimen.Container),
    DerivativeTypeId = (SELECT RowId FROM study.SpecimenDerivative WHERE
        ExternalId = study.Specimen.DerivativeTypeId AND study.SpecimenDerivative.Container = study.Specimen.Container),
    AdditiveTypeId = (SELECT RowId FROM study.SpecimenAdditive WHERE
        ExternalId = study.Specimen.AdditiveTypeId AND study.SpecimenAdditive.Container = study.Specimen.Container);

ALTER TABLE study.Site
    ADD COLUMN Repository BOOLEAN,
    ADD COLUMN Clinic BOOLEAN,
    ADD COLUMN SAL BOOLEAN,
    ADD COLUMN Endpoint BOOLEAN;

UPDATE study.Site SET Repository = IsRepository, Clinic = IsClinic, SAL = IsSAL, Endpoint = IsEndpoint;

ALTER TABLE study.Site
    DROP COLUMN IsRepository,
    DROP COLUMN IsClinic,
    DROP COLUMN IsSAL,
    DROP COLUMN IsEndpoint;

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

ALTER TABLE study.Specimen
    ADD DerivativeTypeId2 INT,
    ADD FrozenTime TIMESTAMP,
    ADD ProcessingTime TIMESTAMP,
    ADD PrimaryVolume FLOAT,
    ADD PrimaryVolumeUnits VARCHAR(20),
    ADD CONSTRAINT FK_Specimens_Derivatives2 FOREIGN KEY (DerivativeTypeId2) REFERENCES study.SpecimenDerivative(RowId);

ALTER TABLE study.SpecimenEvent
    ADD ShippedFromLab INT,
    ADD ShippedToLab INT,
    ADD CONSTRAINT FK_ShippedFromLab_Site FOREIGN KEY (ShippedFromLab) references study.Site(RowId),
    ADD CONSTRAINT FK_ShippedToLab_Site FOREIGN KEY (ShippedToLab) references study.Site(RowId);

CREATE INDEX IX_SpecimenEvent_ShippedFromLab ON study.SpecimenEvent(ShippedFromLab);
CREATE INDEX IX_SpecimenEvent_ShippedToLab ON study.SpecimenEvent(ShippedToLab);

ALTER TABLE study.Site
    ALTER COLUMN LabUploadCode TYPE VARCHAR(10);

ALTER TABLE study.SpecimenAdditive
    ALTER COLUMN LdmsAdditiveCode TYPE VARCHAR(30);

ALTER TABLE study.SpecimenDerivative
    ALTER COLUMN LdmsDerivativeCode TYPE VARCHAR(20);

ALTER TABLE study.Specimen
    ALTER COLUMN GlobalUniqueId TYPE VARCHAR(50),
    ALTER COLUMN ClassId TYPE VARCHAR(20),
    ALTER COLUMN ProtocolNumber TYPE VARCHAR(20),
    ALTER COLUMN VisitDescription TYPE VARCHAR(10),
    ALTER COLUMN VolumeUnits TYPE VARCHAR(20),
    ALTER COLUMN SubAdditiveDerivative TYPE VARCHAR(50);

ALTER TABLE study.SpecimenEvent
    ALTER COLUMN UniqueSpecimenId TYPE VARCHAR(50),
    ALTER COLUMN RecordSource TYPE VARCHAR(20),
    ALTER COLUMN OtherSpecimenId TYPE VARCHAR(50),
    ALTER COLUMN XSampleOrigin TYPE VARCHAR(50),
    ALTER COLUMN SpecimenCondition TYPE VARCHAR(30),
    ALTER COLUMN ExternalLocation TYPE VARCHAR(50);

-- Migrate batch properties from runs to separate batch objects

-- Create batch rows
INSERT INTO exp.experiment (lsid, name, created, createdby, modified, modifiedby, container, hidden, batchprotocolid)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.name || ' Batch', er.created, er.createdby, er.modified, er.modifiedby, er.container, false, p.rowid FROM exp.experimentrun er, exp.protocol p
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%' AND er.protocollsid = p.lsid
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL);

-- Add an entry to the object table
INSERT INTO exp.object (objecturi, container)
SELECT
REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.lsid, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment'),
er.container FROM exp.experimentrun er
WHERE er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL);

-- Flip the properties to hang from the batch
UPDATE exp.ObjectProperty SET ObjectId =
    (SELECT oBatch.ObjectId
        FROM exp.Object oRun, exp.Object oBatch WHERE exp.ObjectProperty.ObjectId = oRun.ObjectId AND
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(oRun.ObjectURI, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment') = oBatch.ObjectURI
    )
WHERE
    PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%')
    AND ObjectId IN (SELECT o.ObjectId FROM exp.Object o, exp.ExperimentRun er WHERE o.ObjectURI = er.LSID AND er.lsid LIKE 'urn:lsid:%:%AssayRun.Folder-%:%'
    AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL));

-- Point the runs at their new batches
INSERT INTO exp.RunList (ExperimentRunId, ExperimentId)
    SELECT er.RowId, e.RowId FROM exp.ExperimentRun er, exp.Experiment e
    WHERE
        e.LSID = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(er.LSID, ':ElispotAssayRun', ':Experiment'), ':MicroarrayAssayRun', ':Experiment'), ':GeneralAssayRun', ':Experiment'), ':NabAssayRun', ':Experiment'), ':LuminexAssayRun', ':Experiment'), ':CBCAssayRun', ':Experiment')
        AND er.RowId NOT IN (SELECT ExperimentRunId FROM exp.RunList rl, exp.Experiment e WHERE rl.ExperimentId = e.RowId AND e.BatchProtocolId IS NOT NULL);

-- Clean out the duplicated batch properties on the runs
DELETE FROM exp.ObjectProperty
    WHERE
        ObjectId IN (SELECT o.ObjectId FROM exp.Object o WHERE o.ObjectURI LIKE 'urn:lsid:%:%AssayRun.Folder-%:%')
        AND PropertyId IN (SELECT dp.PropertyId FROM exp.DomainDescriptor dd, exp.PropertyDomain dp WHERE dd.DomainId = dp.DomainId AND dd.DomainURI LIKE 'urn:lsid:%:AssayDomain-Batch.Folder-%:%');

/* study-9.10-9.20.sql */

ALTER TABLE study.SpecimenComment
  ADD QualityControlFlag BOOLEAN NOT NULL DEFAULT False,
  ADD QualityControlFlagForced BOOLEAN NOT NULL DEFAULT False,
  ADD QualityControlComments VARCHAR(512);

ALTER TABLE study.SpecimenEvent
  ADD Ptid VARCHAR(32),
  ADD DrawTimestamp TIMESTAMP,
  ADD SalReceiptDate TIMESTAMP,
  ADD ClassId VARCHAR(20),
  ADD VisitValue NUMERIC(15,4),
  ADD ProtocolNumber VARCHAR(20),
  ADD VisitDescription VARCHAR(10),
  ADD Volume double precision,
  ADD VolumeUnits VARCHAR(20),
  ADD SubAdditiveDerivative VARCHAR(50),
  ADD PrimaryTypeId INT,
  ADD DerivativeTypeId INT,
  ADD AdditiveTypeId INT,
  ADD DerivativeTypeId2 INT,
  ADD OriginatingLocationId INT,
  ADD FrozenTime TIMESTAMP,
  ADD ProcessingTime TIMESTAMP,
  ADD PrimaryVolume FLOAT,
  ADD PrimaryVolumeUnits VARCHAR(20);

UPDATE study.SpecimenEvent SET
    Ptid = study.Specimen.Ptid,
    DrawTimestamp = study.Specimen.DrawTimestamp,
    SalReceiptDate = study.Specimen.SalReceiptDate,
    ClassId = study.Specimen.ClassId,
    VisitValue = study.Specimen.VisitValue,
    ProtocolNumber = study.Specimen.ProtocolNumber,
    VisitDescription = study.Specimen.VisitDescription,
    Volume = study.Specimen.Volume,
    VolumeUnits = study.Specimen.VolumeUnits,
    SubAdditiveDerivative = study.Specimen.SubAdditiveDerivative,
    PrimaryTypeId = study.Specimen.PrimaryTypeId,
    DerivativeTypeId = study.Specimen.DerivativeTypeId,
    AdditiveTypeId = study.Specimen.AdditiveTypeId,
    DerivativeTypeId2 = study.Specimen.DerivativeTypeId2,
    OriginatingLocationId = study.Specimen.OriginatingLocationId,
    FrozenTime = study.Specimen.FrozenTime,
    ProcessingTime = study.Specimen.ProcessingTime,
    PrimaryVolume = study.Specimen.PrimaryVolume,
    PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen WHERE study.Specimen.RowId = study.SpecimenEvent.SpecimenId;

CREATE INDEX IX_ParticipantVisit_Container ON study.ParticipantVisit(Container);
CREATE INDEX IX_ParticipantVisit_ParticipantId ON study.ParticipantVisit(ParticipantId);
CREATE INDEX IX_ParticipantVisit_SequenceNum ON study.ParticipantVisit(SequenceNum);

ALTER TABLE study.SampleRequestSpecimen
  ADD Orphaned BOOLEAN NOT NULL DEFAULT False;

UPDATE study.SampleRequestSpecimen SET Orphaned = True WHERE RowId IN (
    SELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen
    LEFT OUTER JOIN study.Specimen ON
        study.Specimen.GlobalUniqueId = study.SampleRequestSpecimen.SpecimenGlobalUniqueId AND
        study.Specimen.Container = study.SampleRequestSpecimen.Container
    WHERE study.Specimen.GlobalUniqueId IS NULL
);

-- It was a mistake to make this a "hard" foreign key- query will take care of
-- linking without it, so we just need an index.  This allows us to drop all contents
-- of the table when reloading.
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives2;

CREATE INDEX IX_Specimens_Derivatives2 ON study.Specimen(DerivativeTypeId2);

ALTER TABLE study.Specimen
  ADD AtRepository BOOLEAN NOT NULL DEFAULT False,
  ADD LockedInRequest BOOLEAN NOT NULL DEFAULT False,
  ADD Available BOOLEAN NOT NULL DEFAULT False;

UPDATE study.Specimen SET AtRepository = TRUE
  WHERE CurrentLocation IN (SELECT ss.RowId FROM study.Site ss WHERE ss.Repository = TRUE);

UPDATE study.Specimen SET LockedInRequest = TRUE WHERE RowId IN
(
  SELECT study.Specimen.RowId FROM ((study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = True)
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = False)
      JOIN study.Specimen ON study.Specimen.GlobalUniqueId = map.SpecimenGlobalUniqueId AND study.Specimen.Container = map.Container
);

UPDATE study.Specimen SET Available = 
(
  CASE Requestable
  WHEN True THEN (
      CASE LockedInRequest
      WHEN True THEN False
      ELSE True
      END)
  WHEN False THEN False
  ELSE (
  CASE AtRepository
      WHEN True THEN (
          CASE LockedInRequest
          WHEN True THEN False
          ELSE True
          END)
      ELSE False
      END)
  END
);

ALTER TABLE study.Specimen
  ADD ProcessedByInitials VARCHAR(32),
  ADD ProcessingDate TIMESTAMP,
  ADD ProcessingLocation INT;

ALTER TABLE study.SpecimenEvent
  ADD ProcessedByInitials VARCHAR(32),
  ADD ProcessingDate TIMESTAMP;

ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedFromLab_Site;
ALTER TABLE study.SpecimenEvent DROP CONSTRAINT FK_ShippedToLab_Site;

DROP INDEX study.IX_SpecimenEvent_ShippedFromLab;
DROP INDEX study.IX_SpecimenEvent_ShippedToLab;

ALTER TABLE study.SpecimenEvent
  ALTER COLUMN ShippedFromLab TYPE VARCHAR(32),
  ALTER COLUMN ShippedToLab TYPE VARCHAR(32);
-- ALTER TABLE study.Specimen ALTER COLUMN ClassId NVARCHAR(20);

ALTER TABLE study.Study
  ADD AllowReload BOOLEAN NOT NULL DEFAULT FALSE,
  ADD ReloadInterval INT NULL,
  ADD LastReload TIMESTAMP NULL;

ALTER TABLE study.Study
  ADD ReloadUser UserId;

-- This script creates a hard table to hold static specimen data.  Dynamic data (available counts, etc)
-- is calculated on the fly via aggregates.

-- First, a defensive update to all specimen hash codes- it's important that they are accurate for this upgrade,
-- and there's no way to guarantee that no one has manipulated the specimen data since the hashes were calculated
UPDATE study.Specimen SET SpecimenHash =
(SELECT
    'Fld-' || CAST(core.Containers.RowId AS VARCHAR)
    ||'~'|| CASE WHEN OriginatingLocationId IS NOT NULL THEN CAST(OriginatingLocationId AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN Ptid IS NOT NULL THEN CAST(Ptid AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN DrawTimestamp IS NOT NULL THEN CAST(DrawTimestamp AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN SalReceiptDate IS NOT NULL THEN CAST(SalReceiptDate AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN ClassId IS NOT NULL THEN CAST(ClassId AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN VisitValue IS NOT NULL THEN CAST(VisitValue AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN ProtocolNumber IS NOT NULL THEN CAST(ProtocolNumber AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN PrimaryVolume IS NOT NULL THEN CAST(PrimaryVolume AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN PrimaryVolumeUnits IS NOT NULL THEN CAST(PrimaryVolumeUnits AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN VisitDescription IS NOT NULL THEN CAST(VisitDescription AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN VolumeUnits IS NOT NULL THEN CAST(VolumeUnits AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN SubAdditiveDerivative IS NOT NULL THEN CAST(SubAdditiveDerivative AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN PrimaryTypeId IS NOT NULL THEN CAST(PrimaryTypeId AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN DerivativeTypeId IS NOT NULL THEN CAST(DerivativeTypeId AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN DerivativeTypeId2 IS NOT NULL THEN CAST(DerivativeTypeId2 AS VARCHAR) ELSE '' END
    ||'~'|| CASE WHEN AdditiveTypeId IS NOT NULL THEN CAST(AdditiveTypeId AS VARCHAR) ELSE '' END

FROM study.Specimen InnerSpecimen
JOIN core.Containers ON InnerSpecimen.Container = core.Containers.EntityId
WHERE InnerSpecimen.RowId = study.Specimen.RowId);

UPDATE study.SpecimenComment SET SpecimenHash =
    (SELECT SpecimenHash FROM study.Specimen
    WHERE study.SpecimenComment.Container = study.Specimen.Container AND study.SpecimenComment.GlobalUniqueId = study.Specimen.GlobalUniqueId);

-- Rename 'specimen' to 'vial' to correct a long-standing bad name
ALTER TABLE study.Specimen
    DROP CONSTRAINT FK_SpecimenOrigin_Site;

DROP INDEX study.IX_Specimen_AdditiveTypeId;
DROP INDEX study.IX_Specimen_DerivativeTypeId;
DROP INDEX study.IX_Specimen_OriginatingLocationId;
DROP INDEX study.IX_Specimen_PrimaryTypeId;
DROP INDEX study.IX_Specimen_Ptid;
DROP INDEX study.IX_Specimen_VisitValue;
DROP INDEX study.IX_Specimens_Derivatives2;

ALTER TABLE study.Specimen RENAME TO Vial;

ALTER INDEX study.IX_Specimen_Container RENAME TO IX_Vial_Container;
ALTER INDEX study.IX_Specimen_CurrentLocation RENAME TO IX_Vial_CurrentLocation;
ALTER INDEX study.IX_Specimen_GlobalUniqueId RENAME TO IX_Vial_GlobalUniqueId;
ALTER INDEX study.IX_Specimen_SpecimenHash RENAME TO IX_Vial_Container_SpecimenHash;

-- Next, we create the specimen table, which will hold static properties of a specimen draw (versus a vial)
CREATE TABLE study.Specimen
(
    RowId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    SpecimenHash VARCHAR(256),
    Ptid VARCHAR(32),
    VisitDescription VARCHAR(10),
    VisitValue NUMERIC(15,4),
    VolumeUnits VARCHAR(20),
    PrimaryVolume FLOAT,
    PrimaryVolumeUnits VARCHAR(20),
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
        OriginatingLocationId;

-- after specimen is populated, we create a foreign key column on vial, populate it, and change the type to NOT NULL
ALTER TABLE study.Vial ADD SpecimenId INTEGER;

UPDATE study.Vial SET SpecimenId = (
    SELECT RowId FROM study.Specimen
    WHERE study.Specimen.SpecimenHash = study.Vial.SpecimenHash AND
        study.Specimen.Container = study.Vial.Container
);

ALTER TABLE study.Vial ALTER COLUMN SpecimenId SET NOT NULL;

CREATE INDEX IX_Vial_SpecimenId ON study.Vial(SpecimenId);

ALTER TABLE study.Vial ADD CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId);

ALTER TABLE study.Vial
    DROP COLUMN Ptid,
    DROP COLUMN VisitDescription,
    DROP COLUMN VisitValue,
    DROP COLUMN VolumeUnits,
    DROP COLUMN PrimaryTypeId,
    DROP COLUMN AdditiveTypeId,
    DROP COLUMN DerivativeTypeId,
    DROP COLUMN PrimaryVolume,
    DROP COLUMN PrimaryVolumeUnits,
    DROP COLUMN DerivativeTypeId2,
    DROP COLUMN DrawTimestamp,
    DROP COLUMN SalReceiptDate,
    DROP COLUMN ClassId,
    DROP COLUMN ProtocolNumber,
    DROP COLUMN SubAdditiveDerivative,
    DROP COLUMN OriginatingLocationId;

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
        SUM(CASE Available WHEN True THEN Volume ELSE 0 END) AS AvailableVolume,
        COUNT(GlobalUniqueId) AS VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount,
        (COUNT(GlobalUniqueId) - SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) - SUM(CASE Requestable WHEN False THEN 1 ELSE 0 END)) AS ExpectedAvailableCount
    FROM study.Vial
    GROUP BY Container, SpecimenHash
    ) VialCounts
WHERE study.Specimen.Container = VialCounts.Container AND study.Specimen.SpecimenHash = VialCounts.SpecimenHash;

ALTER TABLE study.Vial
  DROP FrozenTime,
  DROP ProcessingTime,
  DROP ProcessedByInitials,
  DROP ProcessingDate;

ALTER TABLE study.Vial
  ADD PrimaryVolume FLOAT,
  ADD PrimaryVolumeUnits VARCHAR(20);

UPDATE study.Vial SET
  PrimaryVolume = study.Specimen.PrimaryVolume,
  PrimaryVolumeUnits = study.Specimen.PrimaryVolumeUnits
FROM study.Specimen
WHERE study.Specimen.RowId = study.Vial.SpecimenId;

ALTER TABLE study.Specimen
  DROP PrimaryVolume,
  DROP PrimaryVolumeUnits;

ALTER TABLE study.SpecimenEvent RENAME COLUMN SpecimenId TO VialId;

CREATE INDEX IDX_StudyData_ContainerKey ON study.StudyData(Container, _Key);
ANALYZE study.StudyData;