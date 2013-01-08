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
 *  Creates experiment annotation tables in the exp schema base on FuGE-OM types
 *
 *  Author Peter Hussey
 *  LabKey Software
 */

/* exp-0.00-1.10.sql */

CREATE SCHEMA exp;
GO

EXEC sp_addtype 'LSIDtype', 'NVARCHAR(300)';
GO

CREATE TABLE exp.ExperimentRun
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (50) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    ExperimentLSID LSIDtype NULL,
    Comments NTEXT NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL,
    FilePathRoot NVARCHAR(500)
)
GO

CREATE TABLE exp.Data
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (50) NULL,
    SourceApplicationId INT NULL,
    SourceProtocolLSID LSIDtype NULL,
    DataFileUrl NVARCHAR (400) NULL,
    RunId INT NULL,
    Created DATETIME NOT NULL,
    Container EntityId NOT NULL
)
GO


CREATE TABLE exp.DataInput
(
    DataId INT NOT NULL,
    TargetApplicationId INT NOT NULL
)
GO

CREATE TABLE exp.Experiment
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    Hypothesis NVARCHAR (500) NULL,
    ContactId NVARCHAR (100) NULL,
    ExperimentDescriptionURL NVARCHAR (200) NULL,
    Comments NVARCHAR (2000) NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL
)
GO

--  this table to be deleted in 1.2
CREATE TABLE exp.Fraction
(
    MaterialId INT NOT NULL,
    StartPoint FLOAT NULL,
    EndPoint FLOAT NULL,
    ProteinAssay FLOAT NULL
)
GO

CREATE TABLE exp.Material
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (200) NULL,
    SourceApplicationId INT NULL,
    SourceProtocolLSID LSIDtype NULL,
    RunId INT NULL,
    Created DATETIME NOT NULL,
    Container EntityId NOT NULL,
)
GO


CREATE TABLE exp.MaterialInput
(
    MaterialId INT NOT NULL,
    TargetApplicationId INT NOT NULL
)
GO

CREATE TABLE exp.MaterialSource
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    LSID LSIDtype NOT NULL,
    MaterialLSIDPrefix NVARCHAR(200) NULL,
    URLPattern NVARCHAR(200) NULL,
    Description NTEXT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL
)
GO
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT PK_MaterialSource PRIMARY KEY (RowId)
GO
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_Name UNIQUE (Name)
GO
ALTER TABLE exp.MaterialSource
   ADD CONSTRAINT UQ_MaterialSource_LSID UNIQUE (LSID)
GO

--  this table to be deleted in 1.2
CREATE TABLE exp.BioSource
(
    MaterialId INT NOT NULL,
    Individual NVARCHAR(50) NULL,
    SampleOriginDate DATETIME NULL
)
GO

CREATE TABLE exp.Object
(
    ObjectId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    ObjectURI LSIDType NOT NULL,
    OwnerObjectId INT NULL,
    CONSTRAINT PK_Object PRIMARY KEY NONCLUSTERED (ObjectId),
    CONSTRAINT UQ_Object UNIQUE CLUSTERED (Container, ObjectURI)
)
GO
CREATE INDEX IDX_Object_OwnerObjectId ON exp.Object (OwnerObjectId);
GO

CREATE TABLE exp.ObjectProperty
(
    ObjectId INT NOT NULL,  -- FK exp.Object
    PropertyId INT NOT NULL, -- FK exp.PropertyDescriptor
    TypeTag CHAR(1) NOT NULL, -- s string, f float, d datetime, t text
    FloatValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    StringValue NVARCHAR(400) NULL,
    TextValue NTEXT NULL,
    CONSTRAINT PK_ObjectProperty PRIMARY KEY CLUSTERED (ObjectId, PropertyId)
)
GO

CREATE TABLE exp.PropertyDescriptor
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    PropertyURI NVARCHAR(200) NOT NULL,
    OntologyURI NVARCHAR (200) NULL,
    TypeURI NVARCHAR(200) NULL,
    Name NVARCHAR(50) NULL,
    Description NTEXT NULL,
    ValueType NVARCHAR(50) NULL,
    DatatypeURI NVARCHAR(200) NOT NULL DEFAULT 'http://www.w3.org/2001/XMLSchema#string',
    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
)
GO

CREATE TABLE exp.Protocol
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    ProtocolDescription NTEXT NULL,
    ApplicationType NVARCHAR (50) NULL,
    MaxInputMaterialPerInstance INT NULL,
    MaxInputDataPerInstance INT NULL,
    OutputMaterialPerInstance INT NULL,
    OutputDataPerInstance INT NULL,
    OutputMaterialType NVARCHAR (50) NULL,
    OutputDataType NVARCHAR (50) NULL,
    Instrument NVARCHAR (200) NULL,
    Software NVARCHAR (200) NULL,
    ContactId NVARCHAR (100) NULL,
    Created DATETIME NULL,
    EntityId UNIQUEIDENTIFIER NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container EntityId NOT NULL
)
GO


CREATE TABLE exp.ProtocolAction
(
    RowId INT IDENTITY (10, 10) NOT NULL,
    ParentProtocolId INT NOT NULL,
    ChildProtocolId INT NOT NULL,
    Sequence INT NOT NULL
)
GO


CREATE TABLE exp.ProtocolActionPredecessor
(
    ActionId INT NOT NULL,
    PredecessorId INT NOT NULL
)
GO


CREATE TABLE exp.ProtocolApplication
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    LSID LSIDtype NOT NULL,
    Name NVARCHAR (200) NULL,
    CpasType NVARCHAR (50) NULL,
    ProtocolLSID LSIDtype NOT NULL,
    ActivityDate DATETIME NULL,
    Comments NVARCHAR (2000) NULL,
    RunId INT NOT NULL,
    ActionSequence INT NOT NULL
)
GO

CREATE TABLE exp.ProtocolParameter
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ProtocolId INT NOT NULL,
    Name NVARCHAR (200) NULL,
    ValueType NVARCHAR(50) NULL,
    StringValue NVARCHAR (400) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    FileLinkValue NVARCHAR(400) NULL,
    XmlTextValue NTEXT NULL,
    OntologyEntryURI NVARCHAR (200) NULL
)
GO

CREATE TABLE exp.ProtocolApplicationParameter
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    ProtocolApplicationId INT NOT NULL,
    Name NVARCHAR (200) NULL,
    ValueType NVARCHAR(50) NULL,
    StringValue NVARCHAR (400) NULL,
    IntegerValue INT NULL,
    DoubleValue FLOAT NULL,
    DateTimeValue DATETIME NULL,
    FileLinkValue NVARCHAR(400) NULL,
    XmlTextValue NTEXT NULL,
    OntologyEntryURI NVARCHAR (200) NULL
)
GO


ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT PK_ExperimentRun PRIMARY KEY NONCLUSTERED (RowId)
GO
ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.Data
    ADD CONSTRAINT PK_Data PRIMARY KEY NONCLUSTERED (RowId)
GO
ALTER TABLE exp.Data
    ADD CONSTRAINT UQ_Data_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.DataInput
    ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId)
GO

ALTER TABLE exp.Experiment
    ADD CONSTRAINT PK_Experiment PRIMARY KEY (RowId)
GO

ALTER TABLE exp.Experiment
    ADD CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.Fraction
    ADD CONSTRAINT PK_Fraction PRIMARY KEY(    MaterialId)
GO

ALTER TABLE exp.Material
    ADD CONSTRAINT PK_Material PRIMARY KEY NONCLUSTERED (RowId)
GO
ALTER TABLE exp.Material
    ADD CONSTRAINT UQ_Material_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.BioSource
    ADD CONSTRAINT PK_BioSource PRIMARY KEY  (MaterialId)
GO

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT PK_MaterialInput PRIMARY KEY  (MaterialId, TargetApplicationId)
GO

CREATE INDEX IX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId)
GO

ALTER TABLE exp.Protocol
    ADD CONSTRAINT PK_Protocol PRIMARY KEY  (RowId)
GO

ALTER TABLE exp.Protocol
    ADD CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT PK_ProtocolAction PRIMARY KEY  (RowId)
GO
ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence)
GO

ALTER TABLE exp.ProtocolActionPredecessor
    ADD CONSTRAINT PK_ActionPredecessor PRIMARY KEY  (ActionId,PredecessorId)
GO

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT PK_ProtocolApplication PRIMARY KEY NONCLUSTERED  (RowId)
GO

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID)
GO

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId)
GO

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId,Name)
GO

ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId)
GO

ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (ProtocolApplicationId,Name)
GO

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_Experiment FOREIGN KEY (ExperimentLSID)
        REFERENCES exp.Experiment (LSID)
GO

ALTER TABLE exp.ExperimentRun ADD
    CONSTRAINT FK_ExperimentRun_Protocol FOREIGN KEY
    (
        ProtocolLSID
    ) REFERENCES exp.Protocol (
        LSID
    )
GO

CREATE INDEX IX_CL_ExperimentRun_ExperimentLSID ON exp.ExperimentRun(ExperimentLSID)
GO


ALTER TABLE exp.Data ADD
    CONSTRAINT FK_Data_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    )
GO

ALTER TABLE exp.Data ADD
    CONSTRAINT FK_Data_ProtocolApplication FOREIGN KEY
    (
        SourceApplicationID
    ) REFERENCES exp.ProtocolApplication (
        RowId
    )
GO

CREATE CLUSTERED INDEX IX_CL_Data_RunId ON exp.Data(RunId)
GO


ALTER TABLE exp.DataInput ADD
    CONSTRAINT FK_DataInputData_Data FOREIGN KEY
    (
        DataId
    ) REFERENCES exp.Data (
        RowId
    )
GO
ALTER TABLE exp.DataInput ADD
    CONSTRAINT FK_DataInput_ProtocolApplication FOREIGN KEY
    (
        TargetApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    )
GO

CREATE INDEX IX_DataInput_TargetApplicationId ON exp.DataInput(TargetApplicationId)
GO


ALTER TABLE exp.Fraction ADD
    CONSTRAINT FK_Fraction_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    )
GO

ALTER TABLE exp.Material ADD
    CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    )
GO
ALTER TABLE exp.Material ADD
    CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY
    (
        SourceApplicationID
    ) REFERENCES exp.ProtocolApplication (
        RowId
    )
GO

CREATE CLUSTERED INDEX IX_CL_Material_RunId ON exp.Material(RunId)
GO


ALTER TABLE exp.BioSource ADD
    CONSTRAINT FK_BioSource_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    )
GO

ALTER TABLE exp.MaterialInput ADD
    CONSTRAINT FK_MaterialInput_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    )
GO

ALTER TABLE exp.MaterialInput ADD
    CONSTRAINT FK_MaterialInput_ProtocolApplication FOREIGN KEY
    (
        TargetApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    )
GO

-- todo this index is in pqsql script.  Needed here?
-- CREATE INDEX IDX_MaterialInput_TargetApplicationId ON exp.MaterialInput(TargetApplicationId);

ALTER TABLE exp.Object ADD
    CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
        REFERENCES exp.Object (ObjectId)
GO

ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
        REFERENCES exp.Object (ObjectId)
GO

ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId)
        REFERENCES exp.PropertyDescriptor (RowId)
GO

ALTER TABLE exp.ProtocolAction ADD
    CONSTRAINT FK_ProtocolAction_Parent_Protocol FOREIGN KEY
    (
        ParentProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    )
GO

ALTER TABLE exp.ProtocolAction ADD
    CONSTRAINT FK_ProtocolAction_Child_Protocol FOREIGN KEY
    (
        ChildProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    )
GO

ALTER TABLE exp.ProtocolActionPredecessor ADD
    CONSTRAINT FK_ActionPredecessor_Action_ProtocolAction FOREIGN KEY
    (
        ActionId
    ) REFERENCES exp.ProtocolAction (
        RowId
    )
GO

ALTER TABLE exp.ProtocolActionPredecessor ADD
    CONSTRAINT FK_ActionPredecessor_Predecessor_ProtocolAction FOREIGN KEY
    (
        PredecessorId
    ) REFERENCES exp.ProtocolAction (
        RowId
    )
GO

ALTER TABLE exp.ProtocolApplication ADD
    CONSTRAINT FK_ProtocolApplication_ExperimentRun FOREIGN KEY
    (
        RunId
    ) REFERENCES exp.ExperimentRun (
        RowId
    )
GO

ALTER TABLE exp.ProtocolApplication ADD
    CONSTRAINT FK_ProtocolApplication_Protocol FOREIGN KEY
    (
        ProtocolLSID
    ) REFERENCES exp.Protocol (
        LSID
    )
GO

CREATE CLUSTERED INDEX IX_CL_ProtocolApplication_RunId ON exp.ProtocolApplication(RunId)
GO

CREATE INDEX IX_ProtocolApplication_ProtocolLSID ON exp.ProtocolApplication(ProtocolLSID)
GO


ALTER TABLE exp.ProtocolParameter ADD
    CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY
    (
        ProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    )
GO


ALTER TABLE exp.ProtocolApplicationParameter ADD
    CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY
    (
        ProtocolApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    )
GO


-- Create procs used by Ontology Manager
CREATE PROCEDURE exp.getObjectProperties(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SELECT * FROM exp.ObjectPropertiesView
    WHERE Container = @container AND ObjectURI = @lsid
END
GO

CREATE PROCEDURE exp.ensureObject(@container ENTITYID, @lsid LSIDType, @ownerObjectId INTEGER) AS
BEGIN
    DECLARE @objectid AS INTEGER
    SET NOCOUNT ON
    BEGIN TRANSACTION
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectURI=@lsid
        IF (@objectid IS NULL)
        BEGIN
            INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (@container, @lsid, @ownerObjectId)
            SELECT @objectid = @@identity
        END
    COMMIT
    SELECT @objectid
END
GO


CREATE PROCEDURE exp.deleteObject(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SET NOCOUNT ON
        DECLARE @objectid INTEGER
        SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectURI=@lsid
        IF (@objectid IS NULL)
            RETURN
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE ObjectId IN
            (SELECT ObjectId FROM exp.Object WHERE OwnerObjectId = @objectid)
        DELETE exp.ObjectProperty WHERE ObjectId = @objectid
        DELETE exp.Object WHERE OwnerObjectId = @objectid
        DELETE exp.Object WHERE ObjectId = @objectid
    COMMIT
END
GO
--
-- This is the most general set property method
--

CREATE PROCEDURE exp.setProperty(@objectid INTEGER, @propertyuri LSIDType, @datatypeuri LSIDType,
    @tag CHAR(1), @f FLOAT, @s NVARCHAR(400), @d DATETIME, @t NTEXT) AS
BEGIN
    DECLARE @propertyid INTEGER
    SET NOCOUNT ON
    BEGIN TRANSACTION
        SELECT @propertyid = RowId FROM exp.PropertyDescriptor WHERE PropertyURI=@propertyuri
        IF (@propertyid IS NULL)
            BEGIN
            INSERT INTO exp.PropertyDescriptor (PropertyURI, DatatypeURI) VALUES (@propertyuri, @datatypeuri)
            SELECT @propertyid = @@identity
            END
        DELETE exp.ObjectProperty WHERE ObjectId=@objectid AND PropertyId=@propertyid
        INSERT INTO exp.ObjectProperty (ObjectId, PropertyID, TypeTag, FloatValue, StringValue, DateTimeValue, TextValue)
            VALUES (@objectid, @propertyid, @tag, @f, @s, @d, @t)
    COMMIT
END
GO


-- internal methods
CREATE PROCEDURE exp._insertFloatProperty(@objectid INTEGER, @propid INTEGER, @float FLOAT) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
    VALUES (@objectid, @propid, 'f', @float)
END
GO


CREATE PROCEDURE exp._insertDateTimeProperty(@objectid INTEGER, @propid INTEGER, @datetime DATETIME) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
    VALUES (@objectid, @propid, 'd', @datetime)
END
GO

CREATE PROCEDURE exp._insertStringProperty(@objectid INTEGER, @propid INTEGER, @string VARCHAR(400)) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
    VALUES (@objectid, @propid, 's', @string)
END
GO

--
-- Set the same property on multiple objects (e.g. impoirt a column of datea)
--
-- fast method for importing ObjectProperties (need to wrap with datalayer code)
--

CREATE PROCEDURE exp.setFloatProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @float1 FLOAT,
    @objectid2 INTEGER, @float2 FLOAT,
    @objectid3 INTEGER, @float3 FLOAT,
    @objectid4 INTEGER, @float4 FLOAT,
    @objectid5 INTEGER, @float5 FLOAT,
    @objectid6 INTEGER, @float6 FLOAT,
    @objectid7 INTEGER, @float7 FLOAT,
    @objectid8 INTEGER, @float8 FLOAT,
    @objectid9 INTEGER, @float9 FLOAT,
    @objectid10 INTEGER, @float10 FLOAT
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertFloatProperty @objectid1, @propertyid, @float1
        EXEC exp._insertFloatProperty @objectid2, @propertyid, @float2
        EXEC exp._insertFloatProperty @objectid3, @propertyid, @float3
        EXEC exp._insertFloatProperty @objectid4, @propertyid, @float4
        EXEC exp._insertFloatProperty @objectid5, @propertyid, @float5
        EXEC exp._insertFloatProperty @objectid6, @propertyid, @float6
        EXEC exp._insertFloatProperty @objectid7, @propertyid, @float7
        EXEC exp._insertFloatProperty @objectid8, @propertyid, @float8
        EXEC exp._insertFloatProperty @objectid9, @propertyid, @float9
        EXEC exp._insertFloatProperty @objectid10, @propertyid, @float10
    COMMIT
END
GO


CREATE PROCEDURE exp.setStringProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @string1 VARCHAR(400),
    @objectid2 INTEGER, @string2 VARCHAR(400),
    @objectid3 INTEGER, @string3 VARCHAR(400),
    @objectid4 INTEGER, @string4 VARCHAR(400),
    @objectid5 INTEGER, @string5 VARCHAR(400),
    @objectid6 INTEGER, @string6 VARCHAR(400),
    @objectid7 INTEGER, @string7 VARCHAR(400),
    @objectid8 INTEGER, @string8 VARCHAR(400),
    @objectid9 INTEGER, @string9 VARCHAR(400),
    @objectid10 INTEGER, @string10 VARCHAR(400)
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertStringProperty @objectid1, @propertyid, @string1
        EXEC exp._insertStringProperty @objectid2, @propertyid, @string2
        EXEC exp._insertStringProperty @objectid3, @propertyid, @string3
        EXEC exp._insertStringProperty @objectid4, @propertyid, @string4
        EXEC exp._insertStringProperty @objectid5, @propertyid, @string5
        EXEC exp._insertStringProperty @objectid6, @propertyid, @string6
        EXEC exp._insertStringProperty @objectid7, @propertyid, @string7
        EXEC exp._insertStringProperty @objectid8, @propertyid, @string8
        EXEC exp._insertStringProperty @objectid9, @propertyid, @string9
        EXEC exp._insertStringProperty @objectid10, @propertyid, @string10
    COMMIT
END
GO


CREATE PROCEDURE exp.setDateTimeProperties(@propertyid INTEGER,
    @objectid1 INTEGER, @datetime1 DATETIME,
    @objectid2 INTEGER, @datetime2 DATETIME,
    @objectid3 INTEGER, @datetime3 DATETIME,
    @objectid4 INTEGER, @datetime4 DATETIME,
    @objectid5 INTEGER, @datetime5 DATETIME,
    @objectid6 INTEGER, @datetime6 DATETIME,
    @objectid7 INTEGER, @datetime7 DATETIME,
    @objectid8 INTEGER, @datetime8 DATETIME,
    @objectid9 INTEGER, @datetime9 DATETIME,
    @objectid10 INTEGER, @datetime10 DATETIME
    ) AS
BEGIN
    SET NOCOUNT ON
    BEGIN TRANSACTION
        DELETE exp.ObjectProperty WHERE PropertyId=@propertyid AND ObjectId IN (@objectid1, @objectid2, @objectid3, @objectid4, @objectid5, @objectid6, @objectid7, @objectid8, @objectid9, @objectid10)
        EXEC exp._insertDateTimeProperty @objectid1, @propertyid, @datetime1
        EXEC exp._insertDateTimeProperty @objectid2, @propertyid, @datetime2
        EXEC exp._insertDateTimeProperty @objectid3, @propertyid, @datetime3
        EXEC exp._insertDateTimeProperty @objectid4, @propertyid, @datetime4
        EXEC exp._insertDateTimeProperty @objectid5, @propertyid, @datetime5
        EXEC exp._insertDateTimeProperty @objectid6, @propertyid, @datetime6
        EXEC exp._insertDateTimeProperty @objectid7, @propertyid, @datetime7
        EXEC exp._insertDateTimeProperty @objectid8, @propertyid, @datetime8
        EXEC exp._insertDateTimeProperty @objectid9, @propertyid, @datetime9
        EXEC exp._insertDateTimeProperty @objectid10, @propertyid, @datetime10
    COMMIT
END
GO

/* exp-1.10-1.20.sql */

IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id = object_id(N'[exp].[FK_BioSource_Material]') AND OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[BioSource] DROP CONSTRAINT FK_BioSource_Material
GO

IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id = object_id(N'[exp].[FK_Fraction_Material]') AND OBJECTPROPERTY(id, N'IsForeignKey') = 1)
ALTER TABLE [exp].[Fraction] DROP CONSTRAINT FK_Fraction_Material
GO

IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id = object_id(N'[exp].[BioSource]') AND OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[BioSource]
GO

IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id = object_id(N'[exp].[Fraction]') AND OBJECTPROPERTY(id, N'IsUserTable') = 1)
DROP TABLE [exp].[Fraction]
GO

IF EXISTS (SELECT * FROM dbo.sysobjects WHERE id = object_id(N'[exp].[setProperty]') AND OBJECTPROPERTY(id, N'IsProcedure') = 1)
DROP PROCEDURE exp.setProperty
GO

ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name
GO

ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue NVARCHAR(4000) NULL
GO
UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
GO
ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue
GO
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue
GO

ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue NVARCHAR(4000) NULL
GO
UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink'
GO
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue
GO
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue
GO


/*
This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

We're somewhat merging to concepts here.

A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)
*/

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
GO

ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor
GO
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor
GO

EXEC sp_rename @objname = 'exp.PropertyDescriptor', @newname = 'PropertyDescriptorOld'
GO

CREATE TABLE exp.PropertyDescriptor
(
    PropertyId INT IDENTITY (1, 1) NOT NULL,
    PropertyURI NVARCHAR (200) NOT NULL,
    OntologyURI NVARCHAR (200) NULL,
    DomainURI NVARCHAR (200) NULL,
    Name NVARCHAR (200) NULL,
    Description NTEXT NULL,
    RangeURI NVARCHAR (200) NOT NULL DEFAULT ('http://www.w3.org/2001/XMLSchema#string'),
    ConceptURI NVARCHAR (200) NULL,
    Label NVARCHAR (200) NULL,
    SearchTerms NVARCHAR (1000) NULL,
    SemanticType NVARCHAR (200) NULL,
    Format NVARCHAR (50) NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL
)
GO

SET IDENTITY_INSERT exp.PropertyDescriptor ON

INSERT INTO exp.PropertyDescriptor(PropertyId, PropertyURI, OntologyURI, DomainURI, Name,
    Description, RangeURI, Container)
SELECT rowid, PropertyURI, OntologyURI, TypeURI, Name,
    Description, DatatypeURI,
    (SELECT  MAX(CAST (O.Container AS VARCHAR(100)))
            FROM exp.PropertyDescriptorOld PD
                INNER JOIN exp.ObjectProperty OP ON (PD.rowid = OP.PropertyID)
                INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)
            WHERE PDU.rowid = PD.rowid
            GROUP BY PD.rowid
        )
FROM exp.PropertyDescriptorOld PDU
    WHERE PDU.rowid IN
        (SELECT PD.rowid
        FROM exp.PropertyDescriptorOld PD
            INNER JOIN exp.ObjectProperty OP ON (PD.rowid = OP.PropertyID)
            INNER JOIN exp.Object O ON (O.ObjectId = OP.ObjectID)
        GROUP BY PD.rowid
        )

SET IDENTITY_INSERT exp.PropertyDescriptor OFF
GO

DROP TABLE exp.PropertyDescriptorOld
GO

UPDATE exp.PropertyDescriptor
SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%'
GO

ALTER TABLE exp.PropertyDescriptor ADD
    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY  CLUSTERED
    (
        PropertyId
    ),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE  NONCLUSTERED
    (
        PropertyURI
    )

GO

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object
GO
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI)
GO

DROP INDEX exp.Object.IDX_Object_OwnerObjectId
GO

CREATE CLUSTERED INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);
GO

ALTER TABLE exp.ObjectProperty
    ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)

GO

-- index string/float properties
CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue)
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue)
GO
-- put in constraints to catch orphaned data and materials

CREATE VIEW exp._noContainerMaterialView AS
SELECT * FROM exp.Material WHERE
    (runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR
    (container IS NULL)
GO
CREATE VIEW exp._noContainerDataView AS
SELECT * FROM exp.Data WHERE
    (runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR
    (container IS NULL)
GO
CREATE VIEW exp._noContainerObjectView AS
SELECT * FROM exp.Object WHERE ObjectURI IN
    (SELECT LSID FROM exp._noContainerMaterialView UNION SELECT LSID FROM exp._noContainerDataView) OR
    container NOT IN (SELECT entityid FROM core.containers)
GO

DELETE FROM exp.ObjectProperty WHERE
    (objectid IN (SELECT objectid FROM exp._noContainerObjectView))
DELETE FROM exp.Object WHERE objectid IN (SELECT objectid FROM exp._noContainerObjectView)
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._noContainerDataView)
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._noContainerMaterialView)
GO
DROP VIEW exp._noContainerObjectView
GO
DROP VIEW exp._noContainerDataView
GO
DROP VIEW exp._noContainerMaterialView
GO

ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
GO
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
GO
ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
GO

CREATE TABLE exp.DomainDescriptor
(
    DomainId INT IDENTITY (1, 1) NOT NULL,
    Name NVARCHAR (200) NULL,
    DomainURI NVARCHAR (200) NOT NULL,
    Description NTEXT NULL,
    Container ENTITYID NOT NULL,
    Project ENTITYID NOT NULL,
    CONSTRAINT PK_DomainDescriptor PRIMARY KEY CLUSTERED (DomainId),
    CONSTRAINT UQ_DomainDescriptor UNIQUE (DomainURI)
)
GO

CREATE TABLE exp.PropertyDomain
(
    PropertyId INT NOT NULL,
    DomainId INT NOT NULL,
    CONSTRAINT PK_PropertyDomain PRIMARY KEY  CLUSTERED (PropertyId,DomainId),
    CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId)
        REFERENCES exp.PropertyDescriptor (PropertyId),
    CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId)
        REFERENCES exp.DomainDescriptor (DomainId)
)
GO

INSERT INTO exp.DomainDescriptor (DomainURI, Container)
    SELECT DomainURI, Container
    FROM exp.PropertyDescriptor PD WHERE PD.DomainURI IS NOT NULL
    AND NOT EXISTS (SELECT * FROM exp.DomainDescriptor DD WHERE DD.DomainURI=PD.DomainURI)
    GROUP BY DomainURI, Container
GO
INSERT INTO exp.PropertyDomain
    SELECT PD.PropertyId, DD.DomainId
    FROM exp.PropertyDescriptor PD INNER JOIN exp.DomainDescriptor DD
        ON (PD.DomainURI = DD.DomainURI)
GO
ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI
GO

-- fix orphans from bad OntologyManager unit test
DELETE FROM exp.PropertyDescriptor
    WHERE Container = (SELECT C.EntityId FROM core.Containers C WHERE C.Name IS NULL)
    AND PropertyURI LIKE '%Junit.OntologyManager%'
GO

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor
GO
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property
GO
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor
GO
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor
GO
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor
GO
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor
GO
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor
GO

ALTER TABLE exp.PropertyDescriptor
    ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY NONCLUSTERED (PropertyId)
GO
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE CLUSTERED (Project, PropertyURI)
GO

ALTER TABLE exp.DomainDescriptor
    ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY NONCLUSTERED (DomainId)
GO
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE CLUSTERED (Project, DomainURI)
GO

ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
GO
ALTER TABLE exp.PropertyDomain ADD
    CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
GO
ALTER TABLE exp.PropertyDomain ADD
    CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId)
GO

/* exp-1.50-1.60.sql */

ALTER TABLE exp.PropertyDomain ADD
    Required BIT NOT NULL,
    CONSTRAINT DF_Required DEFAULT 0 FOR Required
GO

CREATE TABLE exp.RunList
(
    ExperimentId INT NOT NULL,
    ExperimentRunId INT NOT NULL,
    CONSTRAINT PK_RunList PRIMARY KEY (ExperimentId, ExperimentRunId),
    CONSTRAINT FK_RunList_ExperimentId FOREIGN KEY (ExperimentId)
            REFERENCES exp.Experiment(RowId),
    CONSTRAINT FK_RunList_ExperimentRunId FOREIGN KEY (ExperimentRunId)
            REFERENCES exp.ExperimentRun(RowId) )
GO

INSERT INTO exp.RunList (ExperimentId, ExperimentRunId)
SELECT E.RowId, ER.RowId
   FROM exp.Experiment E INNER JOIN exp.ExperimentRun ER
    ON (E.LSID = ER.ExperimentLSID)
GO
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment
GO
DROP INDEX exp.ExperimentRun.IX_CL_ExperimentRun_ExperimentLSID
GO
ALTER TABLE exp.ExperimentRun DROP COLUMN ExperimentLSID
GO

CREATE TABLE exp.ActiveMaterialSource
(
    Container ENTITYID NOT NULL,
    MaterialSourceLSID LSIDtype NOT NULL,
    CONSTRAINT PK_ActiveMaterialSource PRIMARY KEY (Container),
    CONSTRAINT FK_ActiveMaterialSource_Container FOREIGN KEY (Container)
            REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ActiveMaterialSource_MaterialSourceLSID FOREIGN KEY (MaterialSourceLSID)
            REFERENCES exp.MaterialSource(LSID)
)
GO

ALTER TABLE exp.DataInput
    ADD PropertyId INT NULL
GO

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId)
GO

ALTER TABLE exp.MaterialInput
    ADD PropertyId INT NULL
GO

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId)
GO

DROP INDEX exp.ObjectProperty.IDX_ObjectProperty_StringValue
GO

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue NVARCHAR(4000)NULL
GO

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS NVARCHAR(4000)), TypeTag='s'
    WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t')
GO

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue
GO

UPDATE exp.Material SET CpasType='Material' WHERE CpasType IS NULL
GO

EXEC core.fn_dropifexists 'Experiment', 'exp', 'INDEX', 'IX_Experiment_Container'
GO
EXEC core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container'
GO
EXEC core.fn_dropifexists 'ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID'
GO
EXEC core.fn_dropifexists 'RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId'
GO
EXEC core.fn_dropifexists 'ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId'
GO
EXEC core.fn_dropifexists 'ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId'
GO
EXEC core.fn_dropifexists 'ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId'
GO
EXEC core.fn_dropifexists 'ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId'
GO
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_Container'
GO
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId'
GO
EXEC core.fn_dropifexists 'Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl'
GO
EXEC core.fn_dropifexists 'DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId'
GO
EXEC core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_Container'
GO
EXEC core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId'
GO
EXEC core.fn_dropifexists 'Material', 'exp', 'INDEX', 'IX_Material_CpasType'
GO
EXEC core.fn_dropifexists 'MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId'
GO
EXEC core.fn_dropifexists 'MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container'
GO
EXEC core.fn_dropifexists 'ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID'
GO
EXEC core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
GO
EXEC core.fn_dropifexists 'PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId'
GO
EXEC core.fn_dropifexists 'PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container'
GO
EXEC core.fn_dropifexists 'DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container'
GO
EXEC core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId'
GO
-- redundant to unique constraint
EXEC core.fn_dropifexists 'Object', 'exp', 'INDEX', 'IX_Object_Uri'
GO

CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container)
GO
CREATE CLUSTERED INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container)
GO
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID)
GO
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId)
GO
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId)
GO
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId)
GO
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId)
GO
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId)
GO

CREATE INDEX IX_Data_Container ON exp.Data(Container)
GO
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId)
GO
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId)
GO
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl)
GO

CREATE INDEX IX_Material_Container ON exp.Material(Container)
GO
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId)
GO
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId)
GO
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType)
GO


CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container)
GO
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID ON exp.ActiveMaterialSource(MaterialSourceLSID)
GO


CREATE  INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId)
GO
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID)
GO
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container)
GO
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container)
GO
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId)
GO

ALTER TABLE exp.PropertyDescriptor ADD LookupContainer ENTITYID;
ALTER TABLE exp.PropertyDescriptor ADD LookupSchema VARCHAR(50);
ALTER TABLE exp.PropertyDescriptor ADD LookupQuery VARCHAR(50);

CREATE TABLE exp.list
(
    RowId INT IDENTITY (1, 1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(64) NOT NULL,
    DomainId INT NOT NULL,
    KeyName NVARCHAR(64) NOT NULL,
    KeyType VARCHAR(64) NOT NULL,
    Description NTEXT,
    CONSTRAINT PK_List PRIMARY KEY(RowId),
    CONSTRAINT UQ_LIST UNIQUE(Container, Name),
    CONSTRAINT FK_List_DomainId FOREIGN KEY(DomainId) REFERENCES exp.DomainDescriptor(DomainId)
);


CREATE INDEX IDX_List_DomainId ON exp.List(DomainId);

CREATE TABLE exp.IndexInteger
(
    ListId INT NOT NULL,
    "Key" INT NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexInteger PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexInteger_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexInteger_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexInteger_ObjectId ON exp.IndexInteger(ObjectId);

CREATE TABLE exp.IndexVarchar
(
    ListId INT NOT NULL,
    "Key" VARCHAR(300) NOT NULL,
    ObjectId INT NULL,
    CONSTRAINT PK_IndexVarchar PRIMARY KEY(ListId, "Key"),
    CONSTRAINT FK_IndexVarchar_List FOREIGN KEY(ListId) REFERENCES exp.List(RowId),
    CONSTRAINT FK_IndexVarchar_Object FOREIGN KEY(ObjectId) REFERENCES exp.Object(ObjectId)
);
CREATE INDEX IDX_IndexVarchar_ObjectId ON exp.IndexVarchar(ObjectId);

EXEC core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue'
GO
EXEC core.fn_dropifexists 'ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject'
GO

CREATE INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId)
GO

ALTER TABLE exp.list ADD TitleColumn NVARCHAR(200)
GO

ALTER TABLE exp.materialsource DROP COLUMN urlpattern
GO

ALTER TABLE exp.materialsource ADD
    IdCol1 NVARCHAR(200) NULL,
    IdCol2 NVARCHAR(200) NULL,
    IdCol3 NVARCHAR(200) NULL
GO

ALTER TABLE exp.list ADD
    DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    AllowDelete BIT NOT NULL DEFAULT 1,
    AllowUpload BIT NOT NULL DEFAULT 1,
    AllowExport BIT NOT NULL DEFAULT 1
GO

-- Used to attach discussions to lists
ALTER TABLE exp.IndexInteger ADD
    EntityId ENTITYID
GO

ALTER TABLE exp.IndexVarchar ADD
    EntityId ENTITYID
GO

ALTER TABLE exp.ExperimentRun ALTER COLUMN Name NVARCHAR(100)
GO

-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

UPDATE exp.materialsource SET container = (SELECT dd.container FROM exp.domaindescriptor dd WHERE exp.materialsource.lsid = dd.domainuri)
    WHERE rowid IN (SELECT ms.rowid FROM exp.materialsource ms, exp.domaindescriptor dd WHERE dd.domainuri = ms.lsid AND ms.container != dd.container)
GO

-- Convert "FileLink" URIs to "Attachment" URIs in Lists
UPDATE exp.PropertyDescriptor SET RangeURI = 'http://www.labkey.org/exp/xml#attachment' WHERE PropertyId IN
(
    SELECT DISTINCT op.PropertyId FROM (SELECT ObjectId FROM exp.IndexInteger UNION SELECT ObjectId FROM exp.IndexVarchar) i INNER JOIN
        exp.ObjectProperty op ON i.ObjectId = op.ObjectId INNER JOIN
        exp.PropertyDescriptor pd ON op.PropertyId = pd.PropertyId
    WHERE RangeURI = 'http://cpas.fhcrc.org/exp/xml#fileLink'
)
GO

CREATE INDEX IDX_Material_LSID ON exp.Material(LSID)
GO

-- Clean up duplicate PropertyDescriptor and DomainDescriptors. Two cases:
-- 1. Assay definitions that were deleted before we correctly deleted the domains for the batch, run, and data sets.
-- 2. Duplicate input role domains, cause unknown. At least after the UNIQUE constraints are in place we'll find out if we try to insert dupes again

CREATE TABLE ##PropertyIdsToDelete (PropertyId INT)
GO

-- Grab the PropertyIds for properties that belong to assay domains where the assay has been deleted and we have a dupe
INSERT INTO ##PropertyIdsToDelete SELECT p.propertyid FROM exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.domainid IN
        (SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
            AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
            AND domainuri NOT IN
            (SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%'))
GO

-- Grab the PropertyIds for duplicate input role domains. We want all the DomainIds except the MAX ones for each DomainURI
INSERT INTO ##PropertyIdsToDelete SELECT p.propertyid FROM exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.DomainId IN
    (SELECT DomainId FROM
        exp.DomainDescriptor dd,
        (SELECT COUNT(DomainURI) AS c, MAX(DomainId) AS m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
    WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND pd.DomainId NOT IN
    (SELECT MAX(DomainId) AS m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI)
GO

-- Get rid of lingering uses of these orphaned PropertyDescriptors
DELETE FROM exp.ObjectProperty WHERE PropertyId IN (SELECT PropertyId FROM ##PropertyIdsToDelete)
GO

-- Get rid of the duplicate PropertyDescriptors
DELETE FROM exp.PropertyDomain WHERE PropertyId IN (SELECT PropertyId FROM ##PropertyIdsToDelete)
GO
DELETE FROM exp.PropertyDescriptor WHERE PropertyId IN (SELECT PropertyId FROM ##PropertyIdsToDelete)
GO

DROP TABLE ##PropertyIdsToDelete
GO

-- Get rid of the orphaned assay domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN (SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
    AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
    AND domainuri NOT IN
    (SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%'))
GO

-- Get rid of the duplicate input role domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN
    (SELECT DomainId FROM
        exp.DomainDescriptor dd,
        (SELECT COUNT(DomainURI) AS c, MAX(DomainId) AS m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
    WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND DomainId NOT IN
    (SELECT MAX(DomainId) AS m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI)
GO

ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container)
GO

ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container)
GO

CREATE TABLE exp.PropertyValidator
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    Description NVARCHAR(200),
    TypeURI NVARCHAR(200) NOT NULL,
    Expression TEXT,
    Properties TEXT,
    ErrorMessage TEXT,
    Container ENTITYID NOT NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId)
);
GO

CREATE TABLE exp.ValidatorReference
(
    ValidatorId INT NOT NULL,
    PropertyId INT NOT NULL,

    CONSTRAINT PK_ValidatorReference PRIMARY KEY (ValidatorId, PropertyId),
    CONSTRAINT FK_PropertyValidator_ValidatorId FOREIGN KEY (ValidatorId) REFERENCES exp.PropertyValidator (RowId),
    CONSTRAINT FK_PropertyDescriptor_PropertyId FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId)
);
GO

-- Change StringValue from NVARCHAR(4000) to NTEXT
ALTER TABLE exp.ProtocolApplicationParameter
    ALTER COLUMN StringValue NTEXT NULL
GO

UPDATE exp.protocolapplication SET cpastype = 'ProtocolApplication' WHERE
    cpastype != 'ProtocolApplication' AND
    cpastype != 'ExperimentRun' AND
    cpastype != 'ExperimentRunOutput'
GO

/* exp-8.30-9.10.sql */

-- Migrate from storing roles as property descriptors to storing them as strings on the MaterialInput
ALTER TABLE exp.MaterialInput ADD Role VARCHAR(50)
GO
UPDATE exp.MaterialInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.MaterialInput.PropertyId)
GO
UPDATE exp.MaterialInput SET Role = 'Material' WHERE Role IS NULL
GO
ALTER TABLE exp.MaterialInput ALTER COLUMN Role VARCHAR(50) NOT NULL
GO
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role)
GO

DROP INDEX exp.MaterialInput.IX_MaterialInput_PropertyId
GO
ALTER TABLE exp.MaterialInput DROP CONSTRAINT FK_MaterialInput_PropertyDescriptor
GO
ALTER TABLE exp.MaterialInput DROP COLUMN PropertyId
GO


-- Migrate from storing roles as property descriptors to storing them as strings on the DataInput
ALTER TABLE exp.DataInput ADD Role VARCHAR(50)
GO
UPDATE exp.DataInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.DataInput.PropertyId)
GO
UPDATE exp.DataInput SET Role = 'Data' WHERE Role IS NULL
GO
ALTER TABLE exp.DataInput ALTER COLUMN Role VARCHAR(50) NOT NULL
GO
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role)
GO

DROP INDEX exp.DataInput.IX_DataInput_PropertyId
GO
ALTER TABLE exp.DataInput DROP CONSTRAINT FK_DataInput_PropertyDescriptor
GO
ALTER TABLE exp.DataInput DROP COLUMN PropertyId
GO

-- Clean up the domain and property descriptors for input roles
DELETE FROM exp.PropertyDomain WHERE
    DomainId IN (SELECT DomainId FROM exp.DomainDescriptor WHERE
                    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
                    DomainURI LIKE '%:Domain.Folder-%:DataInputRole')
    OR PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE
                    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
                    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%')
GO

DELETE FROM exp.DomainDescriptor WHERE
    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
    DomainURI LIKE '%:Domain.Folder-%:DataInputRole'
GO

DELETE FROM exp.PropertyDescriptor WHERE
    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%'
GO

ALTER TABLE exp.Experiment ADD Hidden BIT NOT NULL DEFAULT 0;

ALTER TABLE exp.ObjectProperty ADD QcValue NVARCHAR(50) NULL;

ALTER TABLE exp.PropertyDescriptor ADD QcEnabled BIT NOT NULL DEFAULT 0;

ALTER TABLE exp.materialsource ADD
    ParentCol NVARCHAR(200) NULL
GO

ALTER TABLE exp.Experiment ADD
    BatchProtocolId INT NULL
GO

ALTER TABLE exp.Experiment ADD CONSTRAINT
    FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId)
GO

CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId)
GO

ALTER TABLE exp.PropertyDescriptor ADD DefaultValueType NVARCHAR(50);
GO

-- Set default value type to LAST_ENTERED for all non-data assay domain properties EXCEPT those
-- that we know should be entered every time :
UPDATE exp.PropertyDescriptor SET DefaultValueType = 'LAST_ENTERED' WHERE PropertyId IN
(
    SELECT exp.PropertyDescriptor.propertyid FROM exp.PropertyDescriptor
    JOIN exp.PropertyDomain ON
        exp.PropertyDescriptor.PropertyId = exp.PropertyDomain.PropertyId
    JOIN exp.DomainDescriptor ON
        exp.PropertyDomain.DomainId = exp.DomainDescriptor.DomainId
    -- get all assay domains except data domains:
    WHERE exp.DomainDescriptor.DomainURI LIKE '%:AssayDomain-%' AND
    exp.DomainDescriptor.DomainURI NOT LIKE '%:AssayDomain-Data%' AND NOT
        -- first, check for these properties in sample well group domains:
          ( exp.PropertyDescriptor.PropertyURI LIKE '%#SpecimenID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#VisitID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#ParticipantID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#Date')
)
GO

-- Set default value type to LAST_ENTERED for all non-data assay domain properties EXCEPT those
-- that we know should be entered every time:
UPDATE exp.PropertyDescriptor SET DefaultValueType = 'FIXED_EDITABLE' WHERE PropertyId IN
(
    SELECT exp.PropertyDescriptor.propertyid FROM exp.PropertyDescriptor
    JOIN exp.PropertyDomain ON
        exp.PropertyDescriptor.PropertyId = exp.PropertyDomain.PropertyId
    JOIN exp.DomainDescriptor ON
        exp.PropertyDomain.DomainId = exp.DomainDescriptor.DomainId
    -- get all assay domains except data domains:
    WHERE exp.DomainDescriptor.DomainURI LIKE '%:AssayDomain-%' AND
    exp.DomainDescriptor.DomainURI NOT LIKE '%:AssayDomain-Data%' AND
        -- first, check for these properties in sample well group domains:
          ( exp.PropertyDescriptor.PropertyURI LIKE '%#SpecimenID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#VisitID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#ParticipantID' OR
        exp.PropertyDescriptor.PropertyURI LIKE '%#Date')
)
GO

ALTER TABLE exp.PropertyDescriptor ADD Hidden BIT NOT NULL DEFAULT 0;

/* exp-9.10-9.20.sql */

ALTER TABLE exp.Data DROP COLUMN SourceProtocolLSID
GO

ALTER TABLE exp.Material DROP COLUMN SourceProtocolLSID
GO

EXEC sp_rename 'exp.ObjectProperty.QcValue', 'MvIndicator', 'COLUMN';
GO

ALTER TABLE exp.PropertyDescriptor ADD MvEnabled BIT NOT NULL DEFAULT 0
GO

UPDATE exp.PropertyDescriptor SET MvEnabled = QcEnabled
GO

declare @constname sysname
select @constname= so.name
from
sysobjects so inner join sysconstraints sc on (sc.constid = so.id)
inner join sysobjects soc on (sc.id = soc.id)
where so.xtype='D'
and soc.id=object_id('exp.PropertyDescriptor')
and col_name(soc.id, sc.colid) = 'QcEnabled'

declare @cmd VARCHAR(500)
select @cmd='Alter Table exp.PropertyDescriptor DROP CONSTRAINT ' + @constname
select @cmd

exec(@cmd)

ALTER TABLE exp.PropertyDescriptor DROP COLUMN QcEnabled
GO

/* exp-9.20-9.30.sql */

ALTER TABLE exp.PropertyDomain ADD SortOrder INT NOT NULL DEFAULT 0
GO

-- Set the initial sort to be the same as it has thus far
UPDATE exp.PropertyDomain SET SortOrder = PropertyId
GO

ALTER TABLE exp.PropertyDomain ADD ImportAliases NVARCHAR(200)
GO

ALTER TABLE exp.PropertyDomain DROP COLUMN ImportAliases
GO

ALTER TABLE exp.PropertyDescriptor ADD ImportAliases NVARCHAR(200)
GO

ALTER TABLE exp.PropertyDescriptor ADD URL NVARCHAR(200)
GO

-- remove property validator dependency on LSID authority
UPDATE exp.propertyvalidator SET typeuri = 'urn:lsid:labkey.com:' +
    (CASE WHEN charindex('PropertyValidator:range', typeuri) = 0
        THEN 'PropertyValidator:regex'
        ELSE 'PropertyValidator:range' END)
  WHERE typeuri NOT LIKE 'urn:lsid:labkey.com:%'
GO

ALTER TABLE exp.PropertyDescriptor ADD ShownInInsertView BIT NOT NULL DEFAULT 1;
ALTER TABLE exp.PropertyDescriptor ADD ShownInUpdateView BIT NOT NULL DEFAULT 1;
ALTER TABLE exp.PropertyDescriptor ADD ShownInDetailsView BIT NOT NULL DEFAULT 1;

/* exp-9.30-10.10.sql */

ALTER TABLE exp.Data ADD CreatedBy INT
GO
ALTER TABLE exp.Data ADD ModifiedBy INT
GO
ALTER TABLE exp.Data ADD Modified DATETIME
GO

UPDATE exp.Data SET Modified = Created
GO

UPDATE exp.Data SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Data.RunId)
GO

ALTER TABLE exp.Material ADD CreatedBy INT
GO
ALTER TABLE exp.Material ADD ModifiedBy INT
GO
ALTER TABLE exp.Material ADD Modified DATETIME
GO

UPDATE exp.Material SET Modified = Created
GO

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.MaterialSource WHERE exp.MaterialSource.LSID = exp.Material.CpasType)
GO

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Material.RunId)
  WHERE CreatedBy IS NULL
GO

/* exp-10.10-10.20.sql */

ALTER TABLE exp.list
    ADD IndexMetaData BIT NOT NULL DEFAULT 1
GO
