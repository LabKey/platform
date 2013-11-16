/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
 *
 *  Author Peter Hussey
 *  LabKey Software
 */

CREATE SCHEMA exp;

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
    Container ENTITYID NOT NULL
);

ALTER TABLE exp.Protocol
    ADD CONSTRAINT PK_Protocol PRIMARY KEY (RowId);

ALTER TABLE exp.Protocol
    ADD CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID);

CREATE TABLE exp.Experiment
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    Hypothesis VARCHAR (500) NULL,
    ContactId VARCHAR (100) NULL,
    ExperimentDescriptionURL VARCHAR (200) NULL,
    Comments VARCHAR (2000) NULL,
    EntityId ENTITYID NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL
);

ALTER TABLE exp.Experiment
    ADD CONSTRAINT PK_Experiment PRIMARY KEY (RowId);

ALTER TABLE exp.Experiment
    ADD CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID);

SELECT core.fn_dropifexists ('Experiment', 'exp', 'INDEX', 'IX_Experiment_Container');
CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container);
ALTER TABLE exp.Experiment ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.Experiment ADD COLUMN
    BatchProtocolId INT NULL;

ALTER TABLE exp.Experiment ADD CONSTRAINT
    FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId);

CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId);

ALTER TABLE exp.Experiment
    ALTER COLUMN hypothesis TYPE TEXT;

ALTER TABLE exp.Experiment
    ALTER COLUMN comments TYPE TEXT;

CREATE TABLE exp.ExperimentRun
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (50) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    ExperimentLSID LSIDtype NULL,
    Comments TEXT NULL,
    EntityId ENTITYID NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,
    FilePathRoot VARCHAR(500)
);

ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT PK_ExperimentRun PRIMARY KEY (RowId);
ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID);

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_Experiment FOREIGN KEY (ExperimentLSID)
        REFERENCES exp.Experiment (LSID);

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY
    (
        ProtocolLSID
    ) REFERENCES exp.Protocol (
        LSID
    );

CREATE INDEX IDX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID);


ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment;
ALTER TABLE exp.ExperimentRun DROP ExperimentLSID;

SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container');
SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID');
CREATE INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container);
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID);
ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(100);

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
    ActionSequence INT NOT NULL
);

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT PK_ProtocolApplication PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID);

ALTER TABLE exp.ProtocolApplication ADD
    CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    );

ALTER TABLE exp.ProtocolApplication ADD
    CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY
    (
        ProtocolLSID
    ) REFERENCES exp.Protocol (
        LSID
    );

CREATE INDEX IDX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId);

CREATE INDEX IDX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID);


CREATE TABLE exp.Data
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    CpasType VARCHAR (50) NULL,
    SourceApplicationId INT NULL,
    SourceProtocolLSID LSIDtype NULL,
    DataFileUrl VARCHAR (400) NULL,
    RunId INT NULL,
    Created TIMESTAMP NOT NULL,
    Container ENTITYID NOT NULL
);

ALTER TABLE exp.Data
    ADD CONSTRAINT PK_Data PRIMARY KEY (RowId);
ALTER TABLE exp.Data
    ADD CONSTRAINT UQ_Data_LSID UNIQUE (LSID);

ALTER TABLE exp.Data ADD
    CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    );

ALTER TABLE exp.Data ADD
    CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY
    (
        SourceApplicationID
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );

CREATE INDEX IDX_CL_Data_RunId ON exp.Data(RunId);

-- add fk constraints to Data, Material and Object container

ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_Container');
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId');
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl');
CREATE INDEX IX_Data_Container ON exp.Data(Container);
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId);
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl);

ALTER TABLE exp.Data DROP COLUMN SourceProtocolLSID;

ALTER TABLE exp.Data ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Data ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Data ADD COLUMN Modified TIMESTAMP;

--  Make PropertyDescriptor consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

-- We're somewhat merging to concepts here.

-- A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
-- A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
CREATE TABLE exp.PropertyDescriptor
(
    PropertyId SERIAL NOT NULL,
    PropertyURI VARCHAR (200) NOT NULL,
    OntologyURI VARCHAR (200) NULL,
    DomainURI VARCHAR (200) NULL,
    Name VARCHAR (200) NULL,
    Description TEXT NULL,
    RangeURI VARCHAR (200) NOT NULL CONSTRAINT DF_PropertyDescriptor_Range DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
    ConceptURI VARCHAR (200) NULL,
    Label VARCHAR (200) NULL,
    SearchTerms VARCHAR (1000) NULL,
    SemanticType VARCHAR (200) NULL,
    Format VARCHAR (50) NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL
);

ALTER TABLE exp.PropertyDescriptor
    ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId),
    ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI);

ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI;

ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor;
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId);
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (Project, PropertyURI);
SELECT core.fn_dropifexists ('PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container');
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupContainer ENTITYID;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupSchema VARCHAR(50);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupQuery VARCHAR(50);

-- Add the contraints
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN QcEnabled BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.PropertyDescriptor ADD DefaultValueType VARCHAR(50);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.PropertyDescriptor ADD COLUMN MvEnabled BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.PropertyDescriptor DROP COLUMN QcEnabled;

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ImportAliases VARCHAR(200);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN URL VARCHAR(200);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInInsertView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInUpdateView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInDetailsView BOOLEAN NOT NULL DEFAULT '1';

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Dimension BOOLEAN NOT NULL DEFAULT False;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN Measure BOOLEAN NOT NULL DEFAULT False;

CREATE TABLE exp.DataInput
(
    DataId INT NOT NULL,
    TargetApplicationId INT NOT NULL
);

ALTER TABLE exp.DataInput
    ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId);

ALTER TABLE exp.DataInput ADD
    CONSTRAINT FK_DataInputData_Data FOREIGN KEY
    (
        DataId
    ) REFERENCES exp.Data (
        RowId
    );
ALTER TABLE exp.DataInput ADD
    CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY
    (
        TargetApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );

CREATE INDEX IDX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId);

ALTER TABLE exp.DataInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

SELECT core.fn_dropifexists ('DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId');
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId);
-- Migrate from storing roles as property descriptors to storing them as strings on the DataInput
ALTER TABLE exp.DataInput ADD COLUMN Role VARCHAR(50);
ALTER TABLE exp.DataInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);

ALTER TABLE exp.DataInput DROP COLUMN PropertyId;

CREATE TABLE exp.Material
(
    RowId SERIAL NOT NULL,
    LSID LSIDtype NOT NULL,
    Name VARCHAR (200) NULL,
    CpasType VARCHAR (200) NULL,
    SourceApplicationId INT NULL,
    SourceProtocolLSID LSIDtype NULL,
    RunId INT NULL,
    Created TIMESTAMP NOT NULL,
    Container ENTITYID NOT NULL
);

ALTER TABLE exp.Material
    ADD CONSTRAINT PK_Material PRIMARY KEY (RowId);
ALTER TABLE exp.Material
    ADD CONSTRAINT UQ_Material_LSID UNIQUE (LSID);

ALTER TABLE exp.Material ADD
    CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    );
ALTER TABLE exp.Material ADD
    CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY
    (
        SourceApplicationID
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );

CREATE INDEX IDX_CL_Material_RunId ON exp.Material(RunId);


ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_Container');
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId');
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_CpasType');
CREATE INDEX IX_Material_Container ON exp.Material(Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId);
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType);
CREATE INDEX IDX_Material_LSID ON exp.Material(LSID);

ALTER TABLE exp.Material DROP COLUMN SourceProtocolLSID;

ALTER TABLE exp.Material ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Material ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Material ADD COLUMN Modified TIMESTAMP;
ALTER TABLE exp.Material ADD COLUMN LastIndexed TIMESTAMP;

CREATE TABLE exp.MaterialInput
(
    MaterialId INT NOT NULL,
    TargetApplicationId INT NOT NULL
);

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT PK_MaterialInput PRIMARY KEY (MaterialId, TargetApplicationId);

ALTER TABLE exp.MaterialInput ADD
    CONSTRAINT FK_MaterialInput_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    );

ALTER TABLE exp.MaterialInput ADD
    CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY
    (
        TargetApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );

CREATE INDEX IDX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);

ALTER TABLE exp.MaterialInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

SELECT core.fn_dropifexists ('MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId');
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId);

-- Migrate from storing roles as property descriptors to storing them as strings on the MaterialInput
ALTER TABLE exp.MaterialInput ADD COLUMN Role VARCHAR(50);
ALTER TABLE exp.MaterialInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);

ALTER TABLE exp.MaterialInput DROP COLUMN PropertyId;

CREATE TABLE exp.MaterialSource
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(50) NOT NULL,
    LSID LSIDtype NOT NULL,
    MaterialLSIDPrefix VARCHAR(200) NULL,
    URLPattern VARCHAR(200) NULL,
    Description TEXT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL
);

ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name);
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID);

ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name;

SELECT core.fn_dropifexists ('MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container');
CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container);
ALTER TABLE exp.materialsource DROP COLUMN urlpattern;

ALTER TABLE exp.materialsource ADD COLUMN IdCol1 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol2 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol3 VARCHAR(200) NULL;

ALTER TABLE exp.materialsource ADD COLUMN ParentCol VARCHAR(200) NULL;

CREATE TABLE exp.BioSource
(
    MaterialId INT NOT NULL,
    Individual VARCHAR(50) NULL,
    SampleOriginDate TIMESTAMP NULL
);

ALTER TABLE exp.BioSource
    ADD CONSTRAINT PK_BioSource PRIMARY KEY (MaterialId);

ALTER TABLE exp.BioSource ADD
    CONSTRAINT FK_BioSource_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    );

ALTER TABLE exp.BioSource DROP CONSTRAINT FK_BioSource_Material;
DROP TABLE exp.BioSource;

CREATE TABLE exp.Object
(
    ObjectId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    ObjectURI LSIDType NOT NULL,
    OwnerObjectId INT NULL,
    CONSTRAINT PK_Object PRIMARY KEY (ObjectId),
    CONSTRAINT UQ_Object UNIQUE (Container, ObjectURI)
);

CREATE INDEX IDX_Object_OwnerObjectId ON exp.Object (OwnerObjectId);
ALTER TABLE exp.Object ADD
    CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
        REFERENCES exp.Object (ObjectId);

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object;
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI);

DROP INDEX exp.IDX_Object_OwnerObjectId;

CREATE INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);;

ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId');

-- redundant to unique constraint
SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_Uri');

CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId);

-- CONSIDER: CONSTRAINT (Container, OwnerObjectId) --> (Container, ObjectId)


CREATE TABLE exp.ObjectProperty
(
    ObjectId INT NOT NULL,  -- FK exp.Object
    PropertyId INT NOT NULL, -- FK exp.PropertyDescriptor
    TypeTag CHAR(1) NOT NULL, -- s string, f float, d datetime, t text
    FloatValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    StringValue VARCHAR(400) NULL,
    TextValue text NULL,
    CONSTRAINT PK_ObjectProperty PRIMARY KEY (ObjectId, PropertyId)
);


ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
        REFERENCES exp.Object (ObjectId);


ALTER TABLE exp.ObjectProperty
    ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);

-- index string/float properties
CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue);
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue);

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor;
ALTER TABLE exp.ObjectProperty ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);
DROP INDEX exp.IDX_ObjectProperty_StringValue;

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue TYPE VARCHAR(4000);

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue;

SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject');
CREATE INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId);
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue');
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject');

CREATE INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId);

ALTER TABLE exp.ObjectProperty ADD COLUMN QcValue VARCHAR(50);

ALTER TABLE exp.ObjectProperty
    RENAME COLUMN QcValue TO MvIndicator;

CREATE TABLE exp.ProtocolAction
(
    RowId SERIAL NOT NULL,
    ParentProtocolId INT NOT NULL,
    ChildProtocolId INT NOT NULL,
    Sequence INT NOT NULL
);

ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT PK_ProtocolAction PRIMARY KEY (RowId);
ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence);

ALTER TABLE exp.ProtocolAction ADD
    CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY
    (
        ParentProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    );

ALTER TABLE exp.ProtocolAction ADD
    CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY
    (
        ChildProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    );

SELECT core.fn_dropifexists ('ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId');
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId);
CREATE TABLE exp.ProtocolActionPredecessor
(
    ActionId INT NOT NULL,
    PredecessorId INT NOT NULL
);

ALTER TABLE exp.ProtocolActionPredecessor
    ADD CONSTRAINT PK_ActionPredecessor PRIMARY KEY (ActionId,PredecessorId);

ALTER TABLE exp.ProtocolActionPredecessor ADD
    CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY
    (
        ActionId
    ) REFERENCES exp.ProtocolAction (
        RowId
    );

ALTER TABLE exp.ProtocolActionPredecessor ADD
    CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY
    (
        PredecessorId
    ) REFERENCES exp.ProtocolAction (
        RowId
    );

SELECT core.fn_dropifexists ('ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId');
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId);
CREATE TABLE exp.ProtocolParameter
(
    RowId SERIAL NOT NULL,
    ProtocolId INT NOT NULL,
    Name VARCHAR (200) NULL,
    ValueType VARCHAR(50) NULL,
    StringValue VARCHAR (400) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    FileLinkValue VARCHAR(400) NULL,
    XmlTextValue TEXT NULL,
    OntologyEntryURI VARCHAR (200) NULL
);

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId,Name);

ALTER TABLE exp.ProtocolParameter ADD
    CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY
    (
        ProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    );

ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);

ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue;

SELECT core.fn_dropifexists ('ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId');
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId);
CREATE TABLE exp.ProtocolApplicationParameter
(
    RowId SERIAL NOT NULL,
    ProtocolApplicationId INT NOT NULL,
    Name VARCHAR (200) NULL,
    ValueType VARCHAR(50) NULL,
    StringValue VARCHAR (400) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue TIMESTAMP NULL,
    FileLinkValue VARCHAR(400) NULL,
    XmlTextValue TEXT NULL,
    OntologyEntryURI VARCHAR (200) NULL
);


ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (ProtocolApplicationId,Name);

ALTER TABLE exp.ProtocolApplicationParameter ADD
    CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY
    (
        ProtocolApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );


ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue;


SELECT core.fn_dropifexists ('ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId');
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId);
-- Change StringValue from VARCHAR(4000) to TEXT
ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE TEXT;

CREATE TABLE exp.DomainDescriptor
(
    DomainId SERIAL NOT NULL,
    Name VARCHAR (200) NULL,
    DomainURI VARCHAR (200) NOT NULL,
    Description text NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,
    CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId),
    CONSTRAINT UQ_DomainDescriptor UNIQUE (DomainURI)
);

ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor;

ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE (Project, DomainURI);
SELECT core.fn_dropifexists ('DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container');
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container);

ALTER TABLE exp.domaindescriptor ADD COLUMN storageTableName VARCHAR(100);
ALTER TABLE exp.domaindescriptor ADD COLUMN storageSchemaName VARCHAR(100);

CREATE TABLE exp.PropertyDomain
(
    PropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    CONSTRAINT PK_PropertyDomain PRIMARY KEY (PropertyId,DomainId),
    CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId)
        REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId)
        REFERENCES exp.DomainDescriptor (DomainId)
);

ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property;
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor;
ALTER TABLE exp.PropertyDomain ADD CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);
ALTER TABLE exp.PropertyDomain ADD CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId);

ALTER TABLE exp.propertydomain ADD COLUMN Required BOOLEAN NOT NULL DEFAULT '0';

SELECT core.fn_dropifexists ('PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId');
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID);

ALTER TABLE exp.PropertyDomain ADD COLUMN SortOrder INT NOT NULL DEFAULT 0;

CREATE TABLE exp.RunList
(
    ExperimentId INT NOT NULL,
    ExperimentRunId INT NOT NULL,
    CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
    CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId)
            REFERENCES exp.Experiment(RowId),
    CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId)
            REFERENCES exp.ExperimentRun(RowId)
);

SELECT core.fn_dropifexists ('RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId');
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId);

CREATE TABLE exp.ActiveMaterialSource
(
    Container ENTITYID NOT NULL,
    MaterialSourceLSID LSIDtype NOT NULL,
    CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
    CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
            REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
            REFERENCES exp.MaterialSource(LSID)
);

SELECT core.fn_dropifexists ('ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID');
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID ON exp.ActiveMaterialSource(MaterialSourceLSID);
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
    CONSTRAINT PK_List PRIMARY KEY(RowId),
    CONSTRAINT UQ_LIST UNIQUE(Container, Name),
    CONSTRAINT FK_List_DomainId FOREIGN KEY(DomainId) REFERENCES exp.DomainDescriptor(DomainId)
);

CREATE INDEX IDX_List_DomainId ON exp.List(DomainId);

ALTER TABLE exp.list ADD COLUMN TitleColumn VARCHAR(200) NULL;

ALTER TABLE exp.list
    ADD COLUMN DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN AllowDelete BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN AllowUpload BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN AllowExport BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE exp.list
    ADD COLUMN IndexMetaData BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE exp.IndexInteger
(
    ListId INT NOT NULL,
    Key INT NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexInteger PRIMARY KEY(ListId, Key),
    CONSTRAINT FK_IndexInteger_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexInteger_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexInteger_ObjectId ON exp.IndexInteger(ObjectId);

-- Used to attach discussions to lists
ALTER TABLE exp.IndexInteger
    ADD COLUMN EntityId ENTITYID;

CREATE TABLE exp.IndexVarchar
(
    ListId INT NOT NULL,
    Key VARCHAR(300) NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexVarchar PRIMARY KEY(ListId, Key),
    CONSTRAINT FK_IndexVarchar_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexVarchar_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexVarchar_ObjectId ON exp.IndexVarchar(ObjectId);

ALTER TABLE exp.IndexVarchar
    ADD COLUMN EntityId ENTITYID;

CREATE TABLE exp.PropertyValidator
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(50) NOT NULL,
    Description VARCHAR(200),
    TypeURI VARCHAR(200) NOT NULL,
    Expression TEXT,
    Properties TEXT,
    ErrorMessage TEXT,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId)
);

CREATE TABLE exp.ValidatorReference
(
    ValidatorId INT NOT NULL,
    PropertyId INT NOT NULL,

    CONSTRAINT PK_ValidatorReference PRIMARY KEY (ValidatorId, PropertyId),
    CONSTRAINT FK_PropertyValidator_ValidatorId FOREIGN KEY (ValidatorId) REFERENCES exp.PropertyValidator (RowId),
    CONSTRAINT FK_PropertyDescriptor_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);

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


-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA', NULL)
CREATE OR REPLACE FUNCTION exp.ensureObject(ENTITYID, LSIDType, INTEGER) RETURNS INTEGER AS $$
DECLARE
    _container ALIAS FOR $1;
    _lsid ALIAS FOR $2;
    _ownerObjectId ALIAS FOR $3;
    _objectid INTEGER;
BEGIN
-- START TRANSACTION;
        _objectid := (SELECT ObjectId FROM exp.Object WHERE Container=_container AND ObjectURI=_lsid);
        IF (_objectid IS NULL) THEN
            INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (_container, _lsid, _ownerObjectId);
            _objectid := currval('exp.object_objectid_seq');
        END IF;
-- COMMIT;
    RETURN _objectid;
END;
$$ LANGUAGE plpgsql;


-- SELECT exp.deleteObject('00000000-0000-0000-0000-000000000000', 'lsidA')
CREATE OR REPLACE FUNCTION exp.deleteObject(ENTITYID, LSIDType) RETURNS void AS '
DECLARE
    _container ALIAS FOR $1;
    _lsid ALIAS FOR $2;
    _objectid INTEGER;
BEGIN
        _objectid := (SELECT ObjectId FROM exp.Object WHERE Container=_container AND ObjectURI=_lsid);
        IF (_objectid IS NULL) THEN
            RETURN;
        END IF;
--    START TRANSACTION;
        DELETE FROM exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid);
        DELETE FROM exp.ObjectProperty WHERE ObjectId = _objectid;
        DELETE FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid;
        DELETE FROM exp.Object WHERE ObjectId = _objectid;
--    COMMIT;
    RETURN;
END;
' LANGUAGE plpgsql;


-- internal methods

-- SELECT exp._insertFloatProperty(13, 5, 101.0)
CREATE OR REPLACE FUNCTION exp._insertFloatProperty(INTEGER, INTEGER, FLOAT) RETURNS void AS $$
DECLARE
    _objectid ALIAS FOR $1;
    _propid ALIAS FOR $2;
    _float ALIAS FOR $3;
BEGIN
    IF (_propid IS NULL OR _objectid IS NULL) THEN
        RETURN;
    END IF;
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
    VALUES (_objectid, _propid, 'f', _float);
    RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp._insertDateTimeProperty(INTEGER, INTEGER, TIMESTAMP) RETURNS void AS $$
DECLARE
    _objectid ALIAS FOR $1;
    _propid ALIAS FOR $2;
    _datetime ALIAS FOR $3;
BEGIN
    IF (_propid IS NULL OR _objectid IS NULL) THEN
        RETURN;
    END IF;
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
    VALUES (_objectid, _propid, 'd', _datetime);
    RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp._insertStringProperty(INTEGER, INTEGER, VARCHAR(400)) RETURNS void AS $$
DECLARE
    _objectid ALIAS FOR $1;
    _propid ALIAS FOR $2;
    _string ALIAS FOR $3;
BEGIN
    IF (_propid IS NULL OR _objectid IS NULL) THEN
        RETURN;
    END IF;
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
    VALUES (_objectid, _propid, 's', _string);
    RETURN;
END;
$$ LANGUAGE plpgsql;


--
-- Set the same property on multiple objects (e.g. impoirt a column of datea)
--
-- fast method for importing ObjectProperties (need to wrap with datalayer code)
--
-- SELECT exp.setFloatProperties(4, 13, 100.0, 14, 101.0, 15, 102.0, 16, 104.0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
-- SELECT * FROM exp.Object
-- SELECT * FROM exp.PropertyDescriptor
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidB', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidC', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidD', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidE', NULL)
CREATE OR REPLACE FUNCTION exp.setFloatProperties(_propertyid INTEGER,
    _objectid1 INTEGER, _float1 FLOAT,
    _objectid2 INTEGER, _float2 FLOAT,
    _objectid3 INTEGER, _float3 FLOAT,
    _objectid4 INTEGER, _float4 FLOAT,
    _objectid5 INTEGER, _float5 FLOAT,
    _objectid6 INTEGER, _float6 FLOAT,
    _objectid7 INTEGER, _float7 FLOAT,
    _objectid8 INTEGER, _float8 FLOAT,
    _objectid9 INTEGER, _float9 FLOAT,
    _objectid10 INTEGER, _float10 FLOAT
    ) RETURNS void AS '
BEGIN
--    BEGIN TRANSACTION
        DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
        PERFORM exp._insertFloatProperty(_objectid1, _propertyid, _float1);
        PERFORM exp._insertFloatProperty(_objectid2, _propertyid, _float2);
        PERFORM exp._insertFloatProperty(_objectid3, _propertyid, _float3);
        PERFORM exp._insertFloatProperty(_objectid4, _propertyid, _float4);
        PERFORM exp._insertFloatProperty(_objectid5, _propertyid, _float5);
        PERFORM exp._insertFloatProperty(_objectid6, _propertyid, _float6);
        PERFORM exp._insertFloatProperty(_objectid7, _propertyid, _float7);
        PERFORM exp._insertFloatProperty(_objectid8, _propertyid, _float8);
        PERFORM exp._insertFloatProperty(_objectid9, _propertyid, _float9);
        PERFORM exp._insertFloatProperty(_objectid10, _propertyid, _float10);
--    COMMIT
    RETURN;
END;
' LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.setStringProperties
(
    _propertyid INTEGER,
    _objectid1 INTEGER, _string1 VARCHAR(400),
    _objectid2 INTEGER, _string2 VARCHAR(400),
    _objectid3 INTEGER, _string3 VARCHAR(400),
    _objectid4 INTEGER, _string4 VARCHAR(400),
    _objectid5 INTEGER, _string5 VARCHAR(400),
    _objectid6 INTEGER, _string6 VARCHAR(400),
    _objectid7 INTEGER, _string7 VARCHAR(400),
    _objectid8 INTEGER, _string8 VARCHAR(400),
    _objectid9 INTEGER, _string9 VARCHAR(400),
    _objectid10 INTEGER, _string10 VARCHAR(400)
) RETURNS void AS $$
BEGIN
--    BEGIN TRANSACTION
        DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
        PERFORM exp._insertStringProperty(_objectid1, _propertyid, _string1);
        PERFORM exp._insertStringProperty(_objectid2, _propertyid, _string2);
        PERFORM exp._insertStringProperty(_objectid3, _propertyid, _string3);
        PERFORM exp._insertStringProperty(_objectid4, _propertyid, _string4);
        PERFORM exp._insertStringProperty(_objectid5, _propertyid, _string5);
        PERFORM exp._insertStringProperty(_objectid6, _propertyid, _string6);
        PERFORM exp._insertStringProperty(_objectid7, _propertyid, _string7);
        PERFORM exp._insertStringProperty(_objectid8, _propertyid, _string8);
        PERFORM exp._insertStringProperty(_objectid9, _propertyid, _string9);
        PERFORM exp._insertStringProperty(_objectid10, _propertyid, _string10);
--    COMMIT
    RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.setDateTimeProperties(_propertyid INTEGER,
    _objectid1 INTEGER, _datetime1 TIMESTAMP,
    _objectid2 INTEGER, _datetime2 TIMESTAMP,
    _objectid3 INTEGER, _datetime3 TIMESTAMP,
    _objectid4 INTEGER, _datetime4 TIMESTAMP,
    _objectid5 INTEGER, _datetime5 TIMESTAMP,
    _objectid6 INTEGER, _datetime6 TIMESTAMP,
    _objectid7 INTEGER, _datetime7 TIMESTAMP,
    _objectid8 INTEGER, _datetime8 TIMESTAMP,
    _objectid9 INTEGER, _datetime9 TIMESTAMP,
    _objectid10 INTEGER, _datetime10 TIMESTAMP
    ) RETURNS void AS '
BEGIN
--    BEGIN TRANSACTION
        DELETE FROM exp.ObjectProperty WHERE PropertyId=_propertyid AND ObjectId IN (_objectid1, _objectid2, _objectid3, _objectid4, _objectid5, _objectid6, _objectid7, _objectid8, _objectid9, _objectid10);
        PERFORM exp._insertDateTimeProperty(_objectid1, _propertyid, _datetime1);
        PERFORM exp._insertDateTimeProperty(_objectid2, _propertyid, _datetime2);
        PERFORM exp._insertDateTimeProperty(_objectid3, _propertyid, _datetime3);
        PERFORM exp._insertDateTimeProperty(_objectid4, _propertyid, _datetime4);
        PERFORM exp._insertDateTimeProperty(_objectid5, _propertyid, _datetime5);
        PERFORM exp._insertDateTimeProperty(_objectid6, _propertyid, _datetime6);
        PERFORM exp._insertDateTimeProperty(_objectid7, _propertyid, _datetime7);
        PERFORM exp._insertDateTimeProperty(_objectid8, _propertyid, _datetime8);
        PERFORM exp._insertDateTimeProperty(_objectid9, _propertyid, _datetime9);
        PERFORM exp._insertDateTimeProperty(_objectid10, _propertyid, _datetime10);
--    COMMIT
    RETURN;
END;
' LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.deleteObjectById(ENTITYID, INTEGER) RETURNS void AS '
DECLARE
    _container ALIAS FOR $1;
    _inputObjectId ALIAS FOR $2;
    _objectid INTEGER;
BEGIN
        _objectid := (SELECT ObjectId FROM exp.Object WHERE Container=_container AND ObjectId=_inputObjectid);
        IF (_objectid IS NULL) THEN
            RETURN;
        END IF;
--    START TRANSACTION;
        DELETE FROM exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid);
        DELETE FROM exp.ObjectProperty WHERE ObjectId = _objectid;
        DELETE FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid;
        DELETE FROM exp.Object WHERE ObjectId = _objectid;
--    COMMIT;
    RETURN;
END;
' LANGUAGE plpgsql;
