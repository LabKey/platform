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

DROP TABLE study.SampleRequestLDMSSample;
DELETE FROM study.SampleRequestEvent;
DELETE FROM study.SampleRequestRequirement;
DELETE FROM study.SampleRequest;
ALTER TABLE study.LDMSSampleEvent DROP CONSTRAINT FK_LDMSSampleEvent_Site;
ALTER TABLE study.SampleRequestRequirement DROP CONSTRAINT FK_SampleRequestRequirement_Site;
ALTER TABLE study.SampleRequest DROP CONSTRAINT FK_SampleRequest_Site;
ALTER TABLE study.Site DROP CONSTRAINT PK_Site;

ALTER TABLE study.Site
    ADD RowId SERIAL,
    ADD ScharpId INT,
    ADD LdmsLabCode INT,
    ADD LabwareLabCode VARCHAR(20),
    ADD LabUploadCode VARCHAR(2),
    ADD IsSal Boolean,
    ADD IsRepository Boolean,
    ADD IsEndpoint Boolean,
    ADD CONSTRAINT PK_Site PRIMARY KEY (RowId);

UPDATE study.Site SET LdmsLabCode = SiteId;

ALTER TABLE study.Site DROP COLUMN SiteId;

ALTER TABLE study.SampleRequest ADD CONSTRAINT FK_SampleRequest_Site
    FOREIGN KEY (DestinationSiteId) REFERENCES study.Site(RowId);

ALTER TABLE study.SampleRequestRequirement ADD CONSTRAINT FK_SampleRequestRequirement_Site
	FOREIGN KEY (SiteId) REFERENCES study.Site(RowId);

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

DROP TABLE study.LDMSSampleEvent;
DROP TABLE study.LDMSSample;

CREATE TABLE study.SampleRequestSpecimen
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    SampleRequestId INT NOT NULL,
    SpecimenId INT NOT NULL,

    CONSTRAINT PK_SampleRequestSpecimen PRIMARY KEY (RowId),
    CONSTRAINT FK_SampleRequestSpecimen_SampleRequest FOREIGN KEY (SampleRequestId) REFERENCES study.SampleRequest(RowId),
    CONSTRAINT FK_SampleRequestSpecimen_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId)
)