CREATE TABLE study.SpecimenAdditive (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsAdditiveCode NVARCHAR(3),
    LabwareAdditiveCode NVARCHAR(20),
    Additive NVARCHAR(100),
    CONSTRAINT PK_Additives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container)
);

/*
CREATE TABLE study.SpecimenColumnTranslation (
    Container ENTITYID NOT NULL,
    ScharpColumn NVARCHAR(25) NOT NULL,
    LdmsColumn NVARCHAR(25),
    LabwareColumn NVARCHAR(25),
    Datatype NVARCHAR(25),
    IsNotNull Bit
);
*/

CREATE TABLE study.SpecimenDerivative (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    LdmsDerivativeCode NVARCHAR(3),
    LabwareDerivativeCode NVARCHAR(20),
    Derivative NVARCHAR(100),
    CONSTRAINT PK_Derivatives PRIMARY KEY (RowId),
    CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container)
);

DROP TABLE study.SampleRequestLDMSSample;
DELETE FROM study.SampleRequestEvent;
DELETE FROM study.SampleRequestRequirement;
DELETE FROM study.SampleRequest;
ALTER TABLE study.LDMSSampleEvent DROP CONSTRAINT FK_LDMSSampleEvent_Site;
ALTER TABLE study.SampleRequestRequirement DROP CONSTRAINT FK_SampleRequestRequirement_Site;
ALTER TABLE study.SampleRequest DROP CONSTRAINT FK_SampleRequest_Site;
ALTER TABLE study.Site DROP CONSTRAINT PK_Site;

ALTER TABLE study.Site ADD
    RowId INT IDENTITY(1,1),
    ScharpId INT,
    LdmsLabCode INT,
    LabwareLabCode NVARCHAR(20),
    LabUploadCode NVARCHAR(2),
    IsSal Bit,
    IsRepository Bit,
    IsEndpoint Bit,
    CONSTRAINT PK_Site PRIMARY KEY (RowId);
go

UPDATE study.Site SET LdmsLabCode = SiteId;

ALTER TABLE study.Site DROP COLUMN SiteId;

ALTER TABLE study.SampleRequest ADD CONSTRAINT FK_SampleRequest_Site
    FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId);

ALTER TABLE study.SampleRequestRequirement ADD CONSTRAINT FK_SampleRequestRequirement_Site
	FOREIGN KEY (SiteId) REFERENCES study.Site(RowId);

/*
CREATE TABLE study.LabwareSampleType (
    Container ENTITYID NOT NULL,
    LabwareSampleType NVARCHAR(10) NOT NULL,
    PrimaryTypeId INT,
    DerivativeId INT,
    CONSTRAINT PK_LabwareSampleTypesPkey PRIMARY KEY (LabwareSampleType, Container)
);
*/

CREATE TABLE study.SpecimenPrimaryType (
    RowId INT IDENTITY(1,1),
    Container ENTITYID NOT NULL,
    ScharpId INT NOT NULL,
    PrimaryTypeLdmsCode NVARCHAR(5),
    PrimaryTypeLabwareCode NVARCHAR(5),
    PrimaryType NVARCHAR(100),
    CONSTRAINT PK_PrimaryTypes PRIMARY KEY (RowId),
    CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container)
);

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
);

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
);

DROP TABLE study.LDMSSampleEvent;
DROP TABLE study.LDMSSample;

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
go
