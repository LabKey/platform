/*
 * Copyright (c) 2019-2026 LabKey Corporation
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

/*
 *  Creates experiment annotation tables in the exp schema based on FuGE-OM types
 */

CREATE SCHEMA exp;

CREATE SCHEMA expsampleset;

-- Provisioned schema used by DataClassDomainKind
CREATE SCHEMA expdataclass;

CREATE DOMAIN public.LSIDType AS VARCHAR(300);

CREATE TABLE exp.Protocol
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    ProtocolDescription TEXT NULL,
    ApplicationType VARCHAR (50) NULL,
    MaxInputMaterialPerInstance INT NULL,
    MaxInputDataPerInstance INT NULL,
    OutputMaterialPerInstance INT NULL,
    OutputDataPerInstance INT NULL,
    OutputMaterialType VARCHAR (50) NULL,
    OutputDataType VARCHAR (50) NULL,
    Instrument VARCHAR (200) NULL,
    Software VARCHAR (200) NULL,
    ContactId VARCHAR (100) NULL,
    Created TIMESTAMP NULL,
    EntityId ENTITYID NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_Protocol PRIMARY KEY (RowId),
    CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID)
);

CREATE INDEX IDX_Protocol_Container ON exp.Protocol (Container);

ALTER TABLE exp.Protocol ADD CONSTRAINT FK_Protocol_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

ALTER TABLE exp.Protocol ADD COLUMN Status VARCHAR(60);

CREATE TABLE exp.Experiment
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    Hypothesis TEXT NULL,
    ContactId VARCHAR (100) NULL,
    ExperimentDescriptionURL VARCHAR (200) NULL,
    Comments TEXT NULL,
    EntityId ENTITYID NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,
    Hidden BOOLEAN NOT NULL DEFAULT '0',
    BatchProtocolId INT NULL,

    CONSTRAINT PK_Experiment PRIMARY KEY (RowId),
    CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID),
    CONSTRAINT FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container);
CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId);

ALTER TABLE exp.Experiment ADD CONSTRAINT FK_Experiment_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

CREATE TABLE exp.ExperimentRun
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (100) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    Comments TEXT NULL,
    EntityId ENTITYID NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,
    FilePathRoot VARCHAR(500),

    CONSTRAINT PK_ExperimentRun PRIMARY KEY (RowId),
    CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID),
    CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY (ProtocolLSID) REFERENCES exp.Protocol (LSID)
);
CREATE INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container);
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID);

ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_ExperimentRun_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.ExperimentRun ADD JobId INTEGER;

--experiment module depends on pipeline, so this should be ok
ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_JobId FOREIGN KEY (JobId)
        REFERENCES pipeline.statusfiles (RowId);

-- Change exp.ExperimentRun.Name from VARCHAR(100) to VARCHAR(200) to match other experiment table name columns
ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(200);

-- Add a column to track the chaining of original and replaced runs
ALTER TABLE exp.ExperimentRun ADD COLUMN ReplacedByRunId INT;

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_ReplacedByRunId FOREIGN KEY (ReplacedByRunId)
        REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IDX_ExperimentRun_ReplacedByRunId ON exp.ExperimentRun(ReplacedByRunId);

-- Add batchId column to run table
ALTER TABLE exp.ExperimentRun
   ADD BatchId INT;

ALTER TABLE exp.ExperimentRun
  ADD CONSTRAINT fk_ExperimentRun_BatchId FOREIGN KEY (BatchId) REFERENCES exp.Experiment (RowId);

CREATE INDEX IX_ExperimentRun_BatchId
  ON exp.ExperimentRun(BatchId);

ALTER TABLE exp.experimentrun ADD COLUMN objectid INT;

ALTER TABLE exp.experimentrun ALTER COLUMN objectid SET NOT NULL;
CREATE UNIQUE INDEX idx_experimentrun_objectid ON exp.experimentrun (objectid);
ALTER TABLE exp.ExperimentRun ADD LastIndexed TIMESTAMP NULL;

ALTER TABLE exp.ExperimentRun ADD COLUMN WorkflowTask INT;

CREATE INDEX IDX_ExperimentRun_WorkflowTask ON exp.ExperimentRun(WorkflowTask);

CREATE TABLE exp.ProtocolApplication
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    CpasType VARCHAR (50) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    ActivityDate TIMESTAMP NULL,
    Comments VARCHAR (2000) NULL,
    RunId INT NOT NULL,
    ActionSequence INT NOT NULL,

    CONSTRAINT PK_ProtocolApplication PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID),
    CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY (ProtocolLSID) REFERENCES exp.Protocol (LSID)
);
CREATE INDEX IDX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId);
CREATE INDEX IDX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID);

-- add start time, end time, and record count to protocol application table for ETL tasks and others
ALTER TABLE exp.ProtocolApplication ADD COLUMN StartTime TIMESTAMP NULL;
ALTER TABLE exp.ProtocolApplication ADD COLUMN EndTime TIMESTAMP NULL;
ALTER TABLE exp.ProtocolApplication ADD COLUMN RecordCount INT NULL;

ALTER TABLE exp.ProtocolApplication ALTER COLUMN Comments TYPE TEXT;

ALTER TABLE exp.ProtocolApplication ADD EntityId ENTITYID;

ALTER TABLE exp.ProtocolApplication ALTER COLUMN EntityId SET NOT NULL;

ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_Run_WorfklowTask FOREIGN KEY (WorkflowTask) REFERENCES exp.ProtocolApplication (RowId) MATCH SIMPLE ON DELETE SET NULL;

CREATE TABLE exp.Data
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    CpasType VARCHAR (50) NULL,
    SourceApplicationId INT NULL,
    DataFileUrl VARCHAR (400) NULL,
    RunId INT NULL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_Data PRIMARY KEY (RowId),
    CONSTRAINT UQ_Data_LSID UNIQUE (LSID),
    CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY (SourceApplicationID) REFERENCES exp.ProtocolApplication (RowId),
    CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);
CREATE INDEX IDX_CL_Data_RunId ON exp.Data(RunId);
CREATE INDEX IX_Data_Container ON exp.Data(Container);
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId);
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl);

ALTER TABLE exp.Data ADD COLUMN Generated BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE exp.data
  ADD COLUMN description VARCHAR(4000);

ALTER TABLE exp.data
  ADD COLUMN classId INT;

-- Within a DataClass, name must be unique.  If DataClass is null, duplicate names are allowed.
ALTER TABLE exp.data
  ADD CONSTRAINT UQ_Data_DataClass_Name UNIQUE (classId, name);

ALTER TABLE exp.data
   ALTER COLUMN cpastype TYPE varchar(300);

ALTER TABLE exp.Data ADD COLUMN LastIndexed TIMESTAMP NULL;

-- Issue 35817 - widen column to allow for longer paths and file names
ALTER TABLE exp.Data ALTER COLUMN DataFileURL TYPE VARCHAR(600);

ALTER TABLE exp.data ADD COLUMN objectid INT;

ALTER TABLE exp.data ALTER COLUMN objectid SET NOT NULL;
CREATE UNIQUE INDEX idx_data_objectid ON exp.data (objectid);
-- Most major file systems cap file lengths at 255 characters. Let's do the same
ALTER TABLE exp.data ALTER COLUMN Name TYPE VARCHAR(255);

-- Make PropertyDescriptor consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

-- We're somewhat merging to concepts here.

-- A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
-- A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
CREATE TABLE exp.PropertyDescriptor
(
    PropertyId SERIAL NOT NULL,
    PropertyURI VARCHAR (200) NOT NULL,
    Name VARCHAR (200) NULL,
    Description TEXT NULL,
    RangeURI VARCHAR (200) NOT NULL CONSTRAINT DF_PropertyDescriptor_Range DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
    ConceptURI VARCHAR (200) NULL,
    Label VARCHAR (200) NULL,
    Format VARCHAR (50) NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,

    LookupContainer ENTITYID,
    LookupSchema VARCHAR(50),
    LookupQuery VARCHAR(50),
    DefaultValueType VARCHAR(50),
    Hidden BOOLEAN NOT NULL DEFAULT '0',
    MvEnabled BOOLEAN NOT NULL DEFAULT '0',
    ImportAliases VARCHAR(200),
    URL VARCHAR(200),
    ShownInInsertView BOOLEAN NOT NULL DEFAULT '1',
    ShownInUpdateView BOOLEAN NOT NULL DEFAULT '1',
    ShownInDetailsView BOOLEAN NOT NULL DEFAULT '1',
    Dimension BOOLEAN NOT NULL DEFAULT FALSE,
    Measure BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE (Project, PropertyURI),
    CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container)
);

CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN CreatedBy USERID NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Created TIMESTAMP NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ModifiedBy USERID NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Modified TIMESTAMP NULL;
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT FK_PropertyDescriptor_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN FacetingBehaviorType VARCHAR(40) NOT NULL DEFAULT 'AUTOMATIC';
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN lookupschema TYPE VARCHAR(200);
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN lookupquery TYPE VARCHAR(200);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ExcludeFromShifting BOOLEAN NOT NULL DEFAULT False;
ALTER TABLE exp.propertydescriptor ADD COLUMN scale INTEGER NOT NULL DEFAULT 0;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN KeyVariable BOOLEAN NOT NULL DEFAULT False;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN DefaultScale VARCHAR(40) NOT NULL DEFAULT 'LINEAR';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN StorageColumnName VARCHAR(100) NULL;
ALTER TABLE exp.PropertyDescriptor RENAME COLUMN KeyVariable TO RecommendedVariable;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Phi VARCHAR(20) NOT NULL DEFAULT 'NotPHI';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN RedactedText VARCHAR(450) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN mvIndicatorStorageColumnName VARCHAR(120);
ALTER TABLE exp.propertydescriptor ADD TextExpression varchar(200) NULL;
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN PropertyURI TYPE VARCHAR(300);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN PrincipalConceptCode VARCHAR(50) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN SourceOntology VARCHAR(20) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ConceptImportColumn VARCHAR(200) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ConceptLabelColumn VARCHAR(200) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN DerivationDataScope VARCHAR(20) NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN IF NOT EXISTS ConceptSubtree TEXT NULL;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Scannable BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE exp.DataInput
(
    DataId INT NOT NULL,
    TargetApplicationId INT NOT NULL,
    Role VARCHAR(50) NOT NULL,

    CONSTRAINT PK_DataInput PRIMARY KEY (DataId, TargetApplicationId),
    CONSTRAINT FK_DataInputData_Data FOREIGN KEY (DataId) REFERENCES exp.Data (RowId),
    CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY (TargetApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IDX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId);
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);

-- Add reference from DataInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.DataInput ADD COLUMN ProtocolInputId INT NULL;

CREATE INDEX IX_DataInput_ProtocolInputId ON exp.DataInput (ProtocolInputId);

CREATE TABLE exp.Material
(
    RowId INT NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    CpasType VARCHAR (200) NULL,
    SourceApplicationId INT NULL,
    RunId INT NULL,
    Created TIMESTAMP NOT NULL,
    Container ENTITYID NOT NULL,

    CreatedBy INT,
    ModifiedBy INT,
    Modified TIMESTAMP,
    LastIndexed TIMESTAMP,

    CONSTRAINT PK_Material PRIMARY KEY (RowId),
    CONSTRAINT UQ_Material_LSID UNIQUE (LSID),
    CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY(RunId) REFERENCES exp.ExperimentRun (RowId),
    CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY (SourceApplicationID) REFERENCES exp.ProtocolApplication (RowId),
    CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
);

ALTER TABLE exp.material ALTER COLUMN name SET NOT NULL;
ALTER TABLE exp.material ADD description VARCHAR(4000);
ALTER TABLE exp.material ADD COLUMN objectid INT;
ALTER TABLE exp.material ALTER COLUMN objectid SET NOT NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotedFromLSID LSIDtype NULL;
ALTER TABLE exp.Material ADD COLUMN SampleState INT;
ALTER TABLE exp.Material ADD COLUMN AliquotCount INTEGER NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotVolume FLOAT NULL;
ALTER TABLE exp.Material ADD COLUMN AliquotUnit VARCHAR(10) NULL;
ALTER TABLE exp.Material ADD COLUMN MaterialSourceId INT NULL;
ALTER TABLE exp.Material ADD COLUMN MaterialExpDate TIMESTAMP NULL;
ALTER TABLE exp.Material ADD COLUMN StoredAmount DOUBLE PRECISION;
ALTER TABLE exp.Material ADD COLUMN Units VARCHAR(20);
ALTER TABLE exp.Material ADD COLUMN AvailableAliquotCount INTEGER NULL;
ALTER TABLE exp.Material ADD COLUMN AvailableAliquotVolume FLOAT NULL;
ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INT;
ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;

ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_SampleState FOREIGN KEY (SampleState) REFERENCES core.DataStates (RowId);

-- Recreate indices on exp.material
CREATE INDEX IDX_CL_Material_RunId ON exp.material (RunId);
CREATE INDEX IX_Material_Container ON exp.material (Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.material (SourceApplicationId);
CREATE INDEX IX_Material_CpasType ON exp.material (CpasType);
CREATE UNIQUE INDEX idx_material_AK ON exp.material (container, cpastype, name) WHERE cpastype IS NOT NULL;
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid);
CREATE INDEX IDX_material_name_sourceid ON exp.material (name, materialSourceId);
CREATE INDEX IX_Material_RootMaterialRowId ON exp.material (RootMaterialRowId);

CREATE TABLE exp.MaterialInput
(
    MaterialId INT NOT NULL,
    TargetApplicationId INT NOT NULL,
    Role VARCHAR(50) NOT NULL,

    CONSTRAINT PK_MaterialInput PRIMARY KEY (MaterialId, TargetApplicationId),
    CONSTRAINT FK_MaterialInput_Material FOREIGN KEY (MaterialId) REFERENCES exp.Material (RowId),
    CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY (TargetApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IDX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);

-- Add reference from MaterialInput to the ProtocolInputId that it corresponds to
ALTER TABLE exp.MaterialInput ADD COLUMN ProtocolInputId INT NULL;

CREATE INDEX IX_MaterialInput_ProtocolInputId ON exp.MaterialInput (ProtocolInputId);

CREATE TABLE exp.MaterialSource
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(50) NOT NULL,
    LSID LSIDtype NOT NULL,
    MaterialLSIDPrefix VARCHAR(200) NULL,
    Description TEXT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,

    IdCol1 VARCHAR(200) NULL,
    IdCol2 VARCHAR(200) NULL,
    IdCol3 VARCHAR(200) NULL,
    ParentCol VARCHAR(200) NULL,

    CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId),
    CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID)
);
CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container);

ALTER TABLE exp.MaterialSource ADD CONSTRAINT FK_MaterialSource_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
-- Change exp.MaterialSource.Name from VARCHAR(50) to VARCHAR(100). Going to 200 to match other experiment tables
 -- hits limits with domain URIs, etc
ALTER TABLE exp.MaterialSource ALTER COLUMN Name TYPE VARCHAR(100);
ALTER TABLE exp.MaterialSource ADD COLUMN NameExpression VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ALTER COLUMN nameexpression TYPE VARCHAR(500);
ALTER TABLE exp.materialsource ADD COLUMN lastindexed TIMESTAMP NULL;
ALTER TABLE exp.materialsource ADD COLUMN materialparentimportaliasmap VARCHAR(4000) NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN LabelColor VARCHAR(7) NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN MetricUnit VARCHAR(10) NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN AutoLinkTargetContainer ENTITYID NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN AutoLinkCategory VARCHAR(200) NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN AliquotNameExpression VARCHAR(200) NULL;
ALTER TABLE exp.MaterialSource ADD COLUMN Category VARCHAR(20) NULL;

CREATE TABLE exp.Object
(
    ObjectId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    ObjectURI LSIDType NOT NULL,
    OwnerObjectId INT NULL,

    CONSTRAINT PK_Object PRIMARY KEY (ObjectId),
    CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId) REFERENCES exp.Object (ObjectId),
    CONSTRAINT UQ_Object UNIQUE (ObjectURI),
    CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
    -- CONSIDER: CONSTRAINT (Container, OwnerObjectId) --> (Container, ObjectId)
);
CREATE INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId);

ALTER TABLE exp.material ADD CONSTRAINT FK_Material_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);
ALTER TABLE exp.experimentrun ADD CONSTRAINT FK_ExperimentRun_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);

ALTER TABLE exp.material ADD CONSTRAINT FK_Material_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.experimentrun ADD CONSTRAINT FK_ExperimentRun_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);

-- add constraints for lsid -> exp.object
ALTER TABLE exp.data ADD CONSTRAINT FK_Data_Lsid
    FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);
-- add constraints for objectid -> exp.object
ALTER TABLE exp.data ADD CONSTRAINT FK_Data_ObjectId
    FOREIGN KEY (objectid) REFERENCES exp.object (objectid);

CREATE TABLE exp.ObjectProperty
(
    ObjectId INT NOT NULL,  -- FK exp.Object
    PropertyId INT NOT NULL, -- FK exp.PropertyDescriptor
    TypeTag CHAR(1) NOT NULL, -- s string, f float, d datetime, t text
    FloatValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    StringValue VARCHAR(4000) NULL,
    MvIndicator VARCHAR(50) NULL,

    CONSTRAINT PK_ObjectProperty PRIMARY KEY (ObjectId, PropertyId),
    CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId),
    CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);
CREATE INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId);

CREATE TABLE exp.ProtocolAction
(
    RowId SERIAL NOT NULL,
    ParentProtocolId INT NOT NULL,
    ChildProtocolId INT NOT NULL,
    Sequence INT NOT NULL,

    CONSTRAINT PK_ProtocolAction PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence),
    CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY (ParentProtocolId) REFERENCES exp.Protocol (RowId),
    CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY (ChildProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId);

CREATE TABLE exp.ProtocolActionPredecessor
(
    ActionId INT NOT NULL,
    PredecessorId INT NOT NULL,

    CONSTRAINT PK_ActionPredecessor PRIMARY KEY (ActionId, PredecessorId),
    CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY (ActionId) REFERENCES exp.ProtocolAction (RowId),
    CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY (PredecessorId) REFERENCES exp.ProtocolAction (RowId)
);
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId);

CREATE TABLE exp.ProtocolParameter
(
    RowId SERIAL NOT NULL,
    ProtocolId INT NOT NULL,
    Name VARCHAR (200) NULL,
    ValueType VARCHAR(50) NULL,
    StringValue VARCHAR (4000) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    OntologyEntryURI VARCHAR (200) NULL,

    CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId, Name),
    CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId)
);
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId);

CREATE TABLE exp.ProtocolApplicationParameter
(
    RowId SERIAL NOT NULL,
    ProtocolApplicationId INT NOT NULL,
    Name VARCHAR (200) NULL,
    ValueType VARCHAR(50) NULL,
    StringValue TEXT NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    OntologyEntryURI VARCHAR (200) NULL,

    CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId),
    CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (ProtocolApplicationId, Name),
    CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY (ProtocolApplicationId) REFERENCES exp.ProtocolApplication (RowId)
);
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId);

CREATE TABLE exp.DomainDescriptor
(
    DomainId SERIAL NOT NULL,
    Name VARCHAR (200) NULL,
    DomainURI VARCHAR (200) NOT NULL,
    Description text NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,
    StorageTableName VARCHAR(100),
    StorageSchemaName VARCHAR(100),

    CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId)
);

CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container);

-- Finally, add some FKs so we don't get into this horrible state again
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT FK_DomainDescriptor_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
CREATE SEQUENCE exp.domaindescriptor_ts;
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT uq_domaindescriptor UNIQUE (DomainURI, Project);
ALTER TABLE exp.DomainDescriptor ADD _ts BIGINT DEFAULT nextval('exp.domaindescriptor_ts') NOT NULL;
ALTER TABLE exp.DomainDescriptor ADD COLUMN ModifiedBy USERID;
ALTER TABLE exp.DomainDescriptor ADD COLUMN Modified TIMESTAMP DEFAULT now();
ALTER TABLE exp.DomainDescriptor ADD COLUMN TemplateInfo VARCHAR(4000) NULL;
ALTER TABLE exp.DomainDescriptor ALTER COLUMN DomainURI TYPE VARCHAR(300);
ALTER TABLE exp.DomainDescriptor ADD COLUMN SystemFieldConfig VARCHAR NULL;

CREATE TABLE exp.PropertyDomain
(
    PropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    Required BOOLEAN NOT NULL DEFAULT '0',
    SortOrder INT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PropertyDomain PRIMARY KEY (PropertyId, DomainId),
    CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId)
);
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID);

CREATE TABLE exp.RunList
(
    ExperimentId INT NOT NULL,
    ExperimentRunId INT NOT NULL,

    CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
    CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId) REFERENCES exp.Experiment(RowId),
    CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId) REFERENCES exp.ExperimentRun(RowId)
);
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId);

ALTER TABLE exp.RunList ADD COLUMN Created TIMESTAMP;
ALTER TABLE exp.RunList ADD COLUMN CreatedBy INT;

CREATE TABLE exp.list
(
    RowId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(64) NOT NULL,
    DomainId INT NOT NULL,
    KeyName VARCHAR(64) NOT NULL,
    KeyType VARCHAR(64) NOT NULL,
    Description TEXT,
    TitleColumn VARCHAR(200) NULL,

    DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    AllowDelete BOOLEAN NOT NULL DEFAULT TRUE,
    AllowUpload BOOLEAN NOT NULL DEFAULT TRUE,
    AllowExport BOOLEAN NOT NULL DEFAULT TRUE,
    IndexMetaData BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT PK_List PRIMARY KEY(RowId),
    CONSTRAINT UQ_LIST UNIQUE(Container, Name),
    CONSTRAINT FK_List_DomainId FOREIGN KEY(DomainId) REFERENCES exp.DomainDescriptor(DomainId)
);
CREATE INDEX IDX_List_DomainId ON exp.List(DomainId);

ALTER TABLE exp.List ADD CONSTRAINT FK_List_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
-- Use prefix naming to better match new field names
ALTER TABLE exp.List RENAME COLUMN IndexMetaData TO MetaDataIndex;

ALTER TABLE exp.list ADD EntireListIndex BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exp.list ADD EntireListTitleSetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EntireListTitleTemplate VARCHAR(1000) NULL;
ALTER TABLE exp.list ADD EntireListBodySetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EntireListBodyTemplate VARCHAR(1000) NULL;

ALTER TABLE exp.list ADD EachItemIndex BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE exp.list ADD EachItemTitleSetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EachItemTitleTemplate VARCHAR(1000) NULL;
ALTER TABLE exp.list ADD EachItemBodySetting INT NOT NULL DEFAULT 0;
ALTER TABLE exp.list ADD EachItemBodyTemplate VARCHAR(1000) NULL;

ALTER TABLE exp.List ADD LastIndexed TIMESTAMP NULL;

-- Merge the "metadata only" and "entire list data" settings, migrating them to a single boolean (EntireListIndex) plus
-- a setting denoting what to index (EntireListIndexSetting = metadata only (0), item data only (1), or both (2))

ALTER TABLE exp.List ADD EntireListIndexSetting INT NOT NULL DEFAULT 0;  -- Metadata only, the default
ALTER TABLE exp.List DROP MetaDataIndex;

ALTER TABLE exp.List DROP CONSTRAINT PK_List;
ALTER TABLE exp.List ADD CONSTRAINT UQ_RowId UNIQUE (RowId);
-- Now add ListId column, populate it with the current values of RowId, and create the new PK (Container, ListId)
ALTER TABLE exp.List ADD ListId INT NULL;
ALTER TABLE exp.List ALTER ListId SET NOT NULL;
ALTER TABLE exp.List ADD CONSTRAINT PK_List PRIMARY KEY (Container, ListId);

SELECT core.fn_dropifexists('list', 'exp', 'CONSTRAINT', 'UQ_RowId');
SELECT core.fn_dropifexists('list', 'exp', 'COLUMN', 'rowid');

ALTER TABLE exp.list ADD FileAttachmentIndex BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE exp.List ADD COLUMN Category VARCHAR(20) NULL;

ALTER TABLE exp.list ALTER COLUMN Name TYPE VARCHAR(200);

-- These columns have been unused for years. https://github.com/LabKey/platform/pull/4549 cleaned up all code references.
ALTER TABLE exp.List DROP COLUMN EntireListTitleSetting;
ALTER TABLE exp.List DROP COLUMN EachItemTitleSetting;

CREATE TABLE exp.ConditionalFormat
(
    RowId SERIAL NOT NULL,
    PropertyId INT NOT NULL,
    SortOrder INT NOT NULL,
    Filter VARCHAR(500) NOT NULL,
    Bold BOOLEAN NOT NULL,
    Italic BOOLEAN NOT NULL,
    Strikethrough BOOLEAN NOT NULL,
    TextColor VARCHAR(10),
    BackgroundColor VARCHAR(10),

    CONSTRAINT PK_ConditionalFormat_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ConditionalFormat_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT UQ_ConditionalFormat_PropertyId_SortOrder UNIQUE (PropertyId, SortOrder)
);
CREATE INDEX IDX_ConditionalFormat_PropertyId ON exp.ConditionalFormat(PropertyId);

CREATE TABLE exp.AssayQCFlag
(
    RowId SERIAL NOT NULL,
    RunId INT NOT NULL,
    FlagType VARCHAR(40) NOT NULL,
    Description TEXT NULL,
    Comment TEXT NULL,
    Enabled BOOLEAN NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL
);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT PK_AssayQCFlag PRIMARY KEY (RowId);

ALTER TABLE exp.AssayQCFlag ADD CONSTRAINT FK_AssayQCFlag_EunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId);

CREATE INDEX IX_AssayQCFlag_RunId ON exp.AssayQCFlag(RunId);

ALTER TABLE exp.AssayQCFlag ADD COLUMN IntKey1 INT NULL;
ALTER TABLE exp.AssayQCFlag ADD COLUMN IntKey2 INT NULL;

CREATE INDEX IX_AssayQCFlag_IntKeys ON exp.AssayQCFlag(IntKey1, IntKey2);

ALTER TABLE exp.AssayQCFlag ADD COLUMN Key1 VARCHAR(50);
ALTER TABLE exp.AssayQCFlag ADD COLUMN Key2 VARCHAR(50);

CREATE INDEX IX_AssayQCFlag_Keys ON exp.AssayQCFlag(Key1, Key2);

CREATE TABLE exp.DataClass
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(200) NOT NULL,
    LSID LSIDtype NOT NULL,
    Container ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Description TEXT NULL,
    MaterialSourceId INT NULL,
    NameExpression VARCHAR(200) NULL,

    CONSTRAINT PK_DataClass PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataClass_LSID UNIQUE (LSID),
    CONSTRAINT UQ_DataClass_Container_Name UNIQUE (Container, Name),

    CONSTRAINT FK_DataClass_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_DataClass_MaterialSource FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);
CREATE INDEX IX_DataClass_Container ON exp.DataClass(Container);

ALTER TABLE exp.data ADD CONSTRAINT FK_Data_DataClass FOREIGN KEY (classId) REFERENCES exp.DataClass (rowid);

ALTER TABLE exp.dataclass ALTER COLUMN nameexpression TYPE VARCHAR(500);

ALTER TABLE exp.DataClass ADD COLUMN Category VARCHAR(20) NULL;

ALTER TABLE exp.dataclass ADD COLUMN lastindexed TIMESTAMP NULL;

ALTER TABLE exp.DataClass ADD COLUMN dataparentimportaliasmap VARCHAR(4000) NULL;

CREATE TABLE exp.Alias
(
    RowId SERIAL NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    Name VARCHAR(500) NOT NULL,

    CONSTRAINT PK_Alias PRIMARY KEY (RowId),
    CONSTRAINT UQ_Alias_Name UNIQUE (Name)
);

CREATE INDEX IX_Alias_Name ON exp.Alias(Name);

CREATE TABLE exp.DataAliasMap
(
    LSID LSIDtype NOT NULL,
    Alias INT NOT NULL,
    Container EntityId NOT NULL,

    CONSTRAINT PK_DataAliasMap PRIMARY KEY (LSID, Alias),
    CONSTRAINT FK_DataAlias_RowId FOREIGN KEY (Alias) REFERENCES exp.Alias(RowId)
);

ALTER TABLE exp.DataAliasMap ADD CONSTRAINT FK_DataAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Data(LSID);
CREATE INDEX IX_DataAliasMap ON exp.DataAliasMap(LSID, Alias, Container);

CREATE TABLE exp.MaterialAliasMap
(
    LSID LSIDtype NOT NULL,
    Alias INT NOT NULL,
    Container EntityId NOT NULL,

    CONSTRAINT PK_MaterialAliasMap PRIMARY KEY (LSID, Alias),
    CONSTRAINT FK_MaterialAlias_RowId FOREIGN KEY (Alias) REFERENCES exp.Alias(RowId)
);

ALTER TABLE exp.MaterialAliasMap ADD CONSTRAINT FK_MaterialAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Material(LSID);
CREATE INDEX IX_MaterialAliasMap ON exp.MaterialAliasMap(LSID, Alias, Container);

CREATE TABLE exp.Edge
(
    FromObjectId INT NOT NULL,
--    FromLsid LSIDtype NOT NULL,
    ToObjectId INT NOT NULL,
--    ToLsid LSIDtype NOT NULL,
    RunId INT NOT NULL,

    CONSTRAINT FK_Edge_From_Object FOREIGN KEY (FromObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_To_Object FOREIGN KEY (ToObjectId) REFERENCES exp.object (objectid),
    CONSTRAINT FK_Edge_RunId_Run FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId),
-- for query performance
    CONSTRAINT UQ_Edge_FromTo_RunId UNIQUE (FromObjectId, ToObjectId, RunId),
    CONSTRAINT UQ_Edge_ToFrom_RunId UNIQUE (ToObjectId, FromObjectId, RunId)
);

ALTER TABLE exp.Edge
    DROP CONSTRAINT UQ_Edge_FromTo_RunId,
    DROP CONSTRAINT UQ_Edge_ToFrom_RunId,

    ALTER COLUMN RunId DROP NOT NULL,

    ADD SourceId INT NULL,
    ADD SourceKey VARCHAR(200) NULL,

    ADD CONSTRAINT FK_Edge_SourceId_Object FOREIGN KEY (SourceId) REFERENCES exp.Object (Objectid),
    ADD CONSTRAINT UQ_Edge_FromTo_RunId_SourceId_SourceKey UNIQUE (FromObjectId, ToObjectId, RunId, SourceId, SourceKey);

CREATE INDEX IX_Edge_ToObjectId ON exp.Edge(ToObjectId);
CREATE INDEX IX_Edge_SourceId ON exp.Edge(SourceId);
CREATE INDEX IDX_Edge_RunId ON exp.Edge(RunId);

CREATE TABLE exp.ProtocolInput
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(300) NOT NULL,
    LSID LSIDtype NOT NULL,
    ProtocolId INT NOT NULL,
    Input BOOLEAN NOT NULL,

    -- One of 'Material' or 'Data'
    ObjectType VARCHAR(8) NOT NULL,

    -- DataClassId may be non-null when ObjectType='Data'
    DataClassId INT NULL,
    -- MaterialSourceId may be non-null when ObjectType='Material'
    MaterialSourceId INT NULL,

    CriteriaName VARCHAR(50) NULL,
    CriteriaConfig TEXT NULL,
    MinOccurs INT NOT NULL,
    MaxOccurs INT NULL,

    CONSTRAINT PK_ProtocolInput_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_ProtocolInput_ProtocolId FOREIGN KEY (ProtocolId) REFERENCES exp.Protocol (RowId),
    CONSTRAINT FK_ProtocolInput_DataClassId FOREIGN KEY (DataClassId) REFERENCES exp.DataClass (RowId),
    CONSTRAINT FK_ProtocolInput_MaterialSourceId FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);

CREATE INDEX IX_ProtocolInput_ProtocolId ON exp.ProtocolInput (ProtocolId);
CREATE INDEX IX_ProtocolInput_DataClassId ON exp.ProtocolInput (DataClassId);
CREATE INDEX IX_ProtocolInput_MaterialSourceId ON exp.ProtocolInput (MaterialSourceId);

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

ALTER TABLE exp.DataInput
  ADD CONSTRAINT FK_DataInput_ProtocolInput FOREIGN KEY (ProtocolInputId) REFERENCES exp.ProtocolInput (RowId);

CREATE TABLE exp.PropertyValidator
(
    RowId        serial not null,
    Name         varchar(50) not null,
    Description  varchar(200),
    TypeURI      varchar(200) not null,
    Expression   text,
    ErrorMessage text,
    Properties   text,
    Container    entityid not null constraint fk_pv_container references core.containers (entityid),
    PropertyId   int not null constraint fk_pv_descriptor references exp.propertydescriptor,
    constraint pk_propertyvalidator primary key (container, propertyid, rowid)
);
CREATE INDEX ix_propertyvalidator_propertyid on exp.PropertyValidator(PropertyId);

CREATE TABLE exp.ObjectLegacyNames
(
    RowId SERIAL NOT NULL,
    ObjectId INT NOT NULL,
    ObjectType VARCHAR(20) NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_ObjectLegacyNames PRIMARY KEY (RowId)
);

CREATE TABLE exp.DataTypeExclusion
(
    RowId SERIAL NOT NULL,
    DataTypeRowId INT NOT NULL,
    DataType VARCHAR(20) NOT NULL,
    ExcludedContainer ENTITYID NOT NULL,
    Created TIMESTAMP,
    CreatedBy INT,
    Modified TIMESTAMP,
    ModifiedBy INT,

    CONSTRAINT PK_DataTypeExclusion PRIMARY KEY (RowId),
    CONSTRAINT UQ_DataTypeExclusion UNIQUE (DataTypeRowId, DataType, ExcludedContainer)
);

/* 24.xxx SQL scripts */

CREATE TABLE exp.MaterialIndexed
(
    MaterialId INT NOT NULL,
    LastIndexed TIMESTAMP NOT NULL,

    CONSTRAINT PK_MaterialIndexing PRIMARY KEY (MaterialId),
    CONSTRAINT FK_MaterialId FOREIGN KEY (MaterialId) REFERENCES exp.Material (RowId) ON DELETE CASCADE
);

INSERT INTO exp.MaterialIndexed (MaterialId, LastIndexed) SELECT RowId, LastIndexed FROM exp.Material WHERE LastIndexed IS NOT NULL;

ALTER TABLE exp.Material DROP COLUMN LastIndexed;

CREATE TABLE exp.DataIndexed
(
    DataId INT NOT NULL,
    LastIndexed TIMESTAMP NOT NULL,

    CONSTRAINT PK_DataIndexing PRIMARY KEY (DataId),
    CONSTRAINT FK_DataId FOREIGN KEY (DataId) REFERENCES exp.Data (RowId) ON DELETE CASCADE
);

INSERT INTO exp.DataIndexed (DataId, LastIndexed) SELECT RowId, LastIndexed FROM exp.Data WHERE LastIndexed IS NOT NULL;

ALTER TABLE exp.Data DROP COLUMN LastIndexed;

CREATE TABLE exp.MaterialAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_MaterialAncestors_MaterialId FOREIGN KEY (RowId) REFERENCES exp.Material (RowId) ON DELETE CASCADE
);

CREATE UNIQUE INDEX UQ_MaterialAncestors_AncestorTypeId_RowId ON exp.MaterialAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_MaterialAncestors_AncestorTypeId_RowId_AncestorRowId ON exp.MaterialAncestors (AncestorTypeId, RowId, AncestorRowId);

CREATE TABLE exp.DataAncestors
(
    RowId INT NOT NULL,
    AncestorRowId INT NOT NULL,
    AncestorTypeId VARCHAR(11),

    CONSTRAINT FK_DataAncestors_DataId FOREIGN KEY (RowId) REFERENCES exp.Data (RowId) ON DELETE CASCADE
);

CREATE UNIQUE INDEX UQ_DataAncestors_AncestorTypeId_RowId ON exp.DataAncestors (AncestorTypeId, RowId);
CREATE INDEX IDX_DataAncestors_AncestorTypeId_RowId_AncestorRowId ON exp.DataAncestors (AncestorTypeId, RowId, AncestorRowId);

DROP INDEX IF EXISTS exp.ix_material_cpastype;
CREATE INDEX ix_material_cpastype ON exp.material (cpastype, rowid);
