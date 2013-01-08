/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

/* exp-0.00-1.10.sql */

CREATE SCHEMA exp;

CREATE DOMAIN public.LSIDType AS VARCHAR(300);

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

CREATE TABLE exp.DataInput
(
    DataId INT NOT NULL,
    TargetApplicationId INT NOT NULL
);

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

CREATE TABLE exp.Fraction
(
    MaterialId INT NOT NULL,
    StartPoint FLOAT NULL,
    EndPoint FLOAT NULL,
    ProteinAssay FLOAT NULL
);

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

CREATE TABLE exp.MaterialInput
(
    MaterialId INT NOT NULL,
    TargetApplicationId INT NOT NULL
);

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

CREATE TABLE exp.BioSource
(
    MaterialId INT NOT NULL,
    Individual VARCHAR(50) NULL,
    SampleOriginDate TIMESTAMP NULL
);

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


CREATE TABLE exp.PropertyDescriptor
(
    RowId SERIAL NOT NULL,
    PropertyURI VARCHAR(200) NOT NULL,
    OntologyURI VARCHAR (200) NULL,
    TypeURI VARCHAR(200) NULL,
    Name VARCHAR(50) NULL,
    Description TEXT NULL,
    ValueType VARCHAR(50) NULL,
    DatatypeURI VARCHAR(200) NOT NULL DEFAULT 'http://www.w3.org/2001/XMLSchema#string',
    CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (RowId),
    CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI)
);


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

CREATE TABLE exp.ProtocolAction
(
    RowId SERIAL NOT NULL,
    ParentProtocolId INT NOT NULL,
    ChildProtocolId INT NOT NULL,
    Sequence INT NOT NULL
);

CREATE TABLE exp.ProtocolActionPredecessor
(
    ActionId INT NOT NULL,
    PredecessorId INT NOT NULL
);

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


ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT PK_ExperimentRun PRIMARY KEY (RowId);
ALTER TABLE exp.ExperimentRun
    ADD CONSTRAINT UQ_ExperimentRun_LSID UNIQUE (LSID);

ALTER TABLE exp.Data
    ADD CONSTRAINT PK_Data PRIMARY KEY (RowId);
ALTER TABLE exp.Data
    ADD CONSTRAINT UQ_Data_LSID UNIQUE (LSID);

ALTER TABLE exp.DataInput
    ADD CONSTRAINT PK_DataInput PRIMARY KEY (DataId,TargetApplicationId);

ALTER TABLE exp.Experiment
    ADD CONSTRAINT PK_Experiment PRIMARY KEY (RowId);

ALTER TABLE exp.Experiment
    ADD CONSTRAINT UQ_Experiment_LSID UNIQUE (LSID);

ALTER TABLE exp.Fraction
    ADD CONSTRAINT PK_Fraction PRIMARY KEY (MaterialId);

ALTER TABLE exp.Material
    ADD CONSTRAINT PK_Material PRIMARY KEY (RowId);
ALTER TABLE exp.Material
    ADD CONSTRAINT UQ_Material_LSID UNIQUE (LSID);

ALTER TABLE exp.BioSource
    ADD CONSTRAINT PK_BioSource PRIMARY KEY (MaterialId);

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT PK_MaterialInput PRIMARY KEY (MaterialId, TargetApplicationId);

ALTER TABLE exp.Protocol
    ADD CONSTRAINT PK_Protocol PRIMARY KEY (RowId);

ALTER TABLE exp.Protocol
    ADD CONSTRAINT UQ_Protocol_LSID UNIQUE (LSID);

ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT PK_ProtocolAction PRIMARY KEY (RowId);
ALTER TABLE exp.ProtocolAction
    ADD CONSTRAINT UQ_ProtocolAction UNIQUE (ParentProtocolId, ChildProtocolId, Sequence);

ALTER TABLE exp.ProtocolActionPredecessor
    ADD CONSTRAINT PK_ActionPredecessor PRIMARY KEY (ActionId,PredecessorId);

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT PK_ProtocolApplication PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolApplication
    ADD CONSTRAINT UQ_ProtocolApp_LSID UNIQUE (LSID);

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT PK_ProtocolParameter PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolParameter
    ADD CONSTRAINT UQ_ProtocolParameter_Ord UNIQUE (ProtocolId,Name);

ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT PK_ProtocolAppParam PRIMARY KEY (RowId);

ALTER TABLE exp.ProtocolApplicationParameter
    ADD CONSTRAINT UQ_ProtocolAppParam_Ord UNIQUE (ProtocolApplicationId,Name);

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


ALTER TABLE exp.Fraction ADD
    CONSTRAINT FK_Fraction_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    );

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


ALTER TABLE exp.BioSource ADD
    CONSTRAINT FK_BioSource_Material FOREIGN KEY
    (
        MaterialId
    ) REFERENCES exp.Material (
        RowId
    );

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

ALTER TABLE exp.Object ADD
    CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
        REFERENCES exp.Object (ObjectId);

ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
        REFERENCES exp.Object (ObjectId);

ALTER TABLE exp.ObjectProperty ADD
    CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId)
        REFERENCES exp.PropertyDescriptor (RowId);

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

ALTER TABLE exp.ProtocolParameter ADD
    CONSTRAINT FK_ProtocolParameter_Protocol FOREIGN KEY
    (
        ProtocolId
    ) REFERENCES exp.Protocol (
        RowId
    );

ALTER TABLE exp.ProtocolApplicationParameter ADD
    CONSTRAINT FK_ProtocolAppParam_ProtocolApp FOREIGN KEY
    (
        ProtocolApplicationId
    ) REFERENCES exp.ProtocolApplication (
        RowId
    );


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

-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA', NULL)
-- SELECT * FROM exp.ObjectPropertiesView


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


-- SELECT exp.deleteObject('00000000-0000-0000-0000-000000000000', 'lsidA')
-- SELECT * FROM exp.ObjectPropertiesView


--
-- This is the most general set property method
--


CREATE OR REPLACE FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, VARCHAR(400), TIMESTAMP, TEXT) RETURNS void AS $$
DECLARE
    _objectid ALIAS FOR $1;
    _propertyuri ALIAS FOR $2;
    _datatypeuri ALIAS FOR $3;
    _tag ALIAS FOR $4;
    _f ALIAS FOR $5;
    _s ALIAS FOR $6;
    _d ALIAS FOR $7;
    _t ALIAS FOR $8;
    _propertyid INTEGER;
BEGIN
--    START TRANSACTION;
        _propertyid := (SELECT RowId FROM exp.PropertyDescriptor WHERE PropertyURI=_propertyuri);
        IF (1=1 OR _propertyid IS NULL) THEN
            INSERT INTO exp.PropertyDescriptor (PropertyURI, DatatypeURI) VALUES (_propertyuri, _datatypeuri);
            _propertyid := currval('exp.propertydescriptor_rowid_seq');
        END IF;
        DELETE FROM exp.ObjectProperty WHERE ObjectId=_objectid AND PropertyId=_propertyid;
        INSERT INTO exp.ObjectProperty (ObjectId, PropertyID, TypeTag, FloatValue, StringValue, DateTimeValue, TextValue)
            VALUES (_objectid, _propertyid, _tag, _f, _s, _d, _t);
--    COMMIT;
    RETURN;
END;
$$ LANGUAGE plpgsql;


-- SELECT exp.setProperty(13, 'lsidPROP', 'lsidTYPE', 'f', 1.0, NULL, NULL, NULL)
-- SELECT * FROM exp.ObjectPropertiesView

-- internal methods


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

-- SELECT exp._insertFloatProperty(13, 5, 101.0)


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


-- SELECT exp.setFloatProperties(4, 13, 100.0, 14, 101.0, 15, 102.0, 16, 104.0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
-- SELECT * FROM exp.Object
-- SELECT * FROM exp.PropertyDescriptor
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidB', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidC', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidD', NULL)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidE', NULL)


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

/* exp-1.10-1.20.sql */

ALTER TABLE exp.BioSource DROP CONSTRAINT FK_BioSource_Material;
ALTER TABLE exp.Fraction DROP CONSTRAINT FK_Fraction_Material;

DROP TABLE exp.BioSource;
DROP TABLE exp.Fraction;
DROP FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, VARCHAR(400), TIMESTAMP, TEXT);

ALTER TABLE exp.MaterialSource
   DROP CONSTRAINT UQ_MaterialSource_Name;

ALTER TABLE exp.ProtocolParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);

UPDATE exp.ProtocolParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolParameter DROP COLUMN XmlTextValue;

ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE VARCHAR(4000);

UPDATE exp.ProtocolApplicationParameter
    SET StringValue = FileLinkValue WHERE ValueType='FileLink';

ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN FileLinkValue;
ALTER TABLE exp.ProtocolApplicationParameter DROP COLUMN XmlTextValue;


--This update makes the PropertyDescriptor more consistent with OWL terms, and also work for storing NCI_Thesaurus concepts

--We're somewhat merging to concepts here.

--A PropertyDescriptor with no Domain is a concept (or a Class in OWL).
--A PropertyDescriptor with a Domain describes a member of a type (or an ObjectProperty in OWL)

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor;

--ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor;

ALTER TABLE exp.PropertyDescriptor RENAME TO PropertyDescriptorOld;

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

INSERT INTO exp.PropertyDescriptor(PropertyId, PropertyURI, OntologyURI, DomainURI, Name,
    Description, RangeURI, Container)
SELECT rowid, PropertyURI, OntologyURI, TypeURI, Name,
    Description, DatatypeURI,
    (SELECT MAX(CAST (O.Container AS VARCHAR(100)))
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
        );

DROP TABLE exp.PropertyDescriptorOld;

UPDATE exp.PropertyDescriptor
    SET ConceptURI = RangeURI, RangeURI = 'http://www.w3.org/2001/XMLSchema#string'
    WHERE RangeURI NOT LIKE 'http://www.w3.org/2001/XMLSchema#%';

ALTER TABLE exp.PropertyDescriptor
    ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId),
    ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (PropertyURI);

ALTER TABLE exp.Object DROP CONSTRAINT UQ_Object;
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (ObjectURI);

DROP INDEX exp.IDX_Object_OwnerObjectId;

CREATE INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);;

ALTER TABLE exp.ObjectProperty
    ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);

-- index string/float properties
CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue);
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue);

-- add fk constraints to Data, Material and Object container

CREATE VIEW exp._noContainerMaterialView AS
SELECT * FROM exp.Material WHERE
    (runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR
    (container IS NULL);

CREATE VIEW exp._noContainerDataView AS
SELECT * FROM exp.Data WHERE
    (runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR
    (container IS NULL);

CREATE VIEW exp._noContainerObjectView AS
SELECT * FROM exp.Object WHERE ObjectURI IN
    (SELECT LSID FROM exp._noContainerMaterialView UNION SELECT LSID FROM exp._noContainerDataView) OR
    container NOT IN (SELECT entityid FROM core.containers);

DELETE FROM exp.ObjectProperty WHERE
    (objectid IN (SELECT objectid FROM exp._noContainerObjectView));
DELETE FROM exp.Object WHERE objectid IN (SELECT objectid FROM exp._noContainerObjectView);
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._noContainerDataView);
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._noContainerMaterialView);

DROP VIEW exp._noContainerObjectView;
DROP VIEW exp._noContainerDataView;
DROP VIEW exp._noContainerMaterialView;

ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

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

INSERT INTO exp.DomainDescriptor (DomainURI, Container)
    SELECT DomainURI, Container
    FROM exp.PropertyDescriptor PD WHERE PD.DomainURI IS NOT NULL
    AND NOT EXISTS (SELECT * FROM exp.DomainDescriptor DD WHERE DD.DomainURI=PD.DomainURI)
    GROUP BY DomainURI, Container;

INSERT INTO exp.PropertyDomain
    SELECT PD.PropertyId, DD.DomainId
    FROM exp.PropertyDescriptor PD INNER JOIN exp.DomainDescriptor DD
        ON (PD.DomainURI = DD.DomainURI);

ALTER TABLE exp.PropertyDescriptor DROP COLUMN DomainURI;

-- fix orphans from bad OntologyManager unit test
DELETE FROM exp.PropertyDescriptor
    WHERE Container = (SELECT C.EntityId FROM core.Containers C WHERE C.Name IS NULL)
    AND PropertyURI LIKE '%Junit.OntologyManager%';

ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_PropertyDescriptor;
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_Property;
ALTER TABLE exp.PropertyDomain DROP CONSTRAINT FK_PropertyDomain_DomainDescriptor;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT UQ_PropertyDescriptor;
ALTER TABLE exp.PropertyDescriptor DROP CONSTRAINT PK_PropertyDescriptor;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT UQ_DomainDescriptor;
ALTER TABLE exp.DomainDescriptor DROP CONSTRAINT PK_DomainDescriptor;

ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT PK_PropertyDescriptor PRIMARY KEY (PropertyId);
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE (Project, PropertyURI);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT PK_DomainDescriptor PRIMARY KEY (DomainId);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE (Project, DomainURI);
ALTER TABLE exp.ObjectProperty ADD CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);
ALTER TABLE exp.PropertyDomain ADD CONSTRAINT FK_PropertyDomain_Property FOREIGN KEY (PropertyId) REFERENCES exp.PropertyDescriptor (PropertyId);
ALTER TABLE exp.PropertyDomain ADD CONSTRAINT FK_PropertyDomain_DomainDescriptor FOREIGN KEY (DomainId) REFERENCES exp.DomainDescriptor (DomainId);

ALTER TABLE exp.propertydomain ADD COLUMN Required BOOLEAN NOT NULL DEFAULT '0';

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

INSERT INTO exp.RunList (ExperimentId, ExperimentRunId)
SELECT E.RowId, ER.RowId
   FROM exp.Experiment E INNER JOIN exp.ExperimentRun ER
    ON (E.LSID = ER.ExperimentLSID);

ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_Experiment;
ALTER TABLE exp.ExperimentRun DROP ExperimentLSID;

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

ALTER TABLE exp.DataInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.DataInput
    ADD CONSTRAINT FK_DataInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

ALTER TABLE exp.MaterialInput
    ADD COLUMN PropertyId INT NULL;

ALTER TABLE exp.MaterialInput
    ADD CONSTRAINT FK_MaterialInput_PropertyDescriptor FOREIGN KEY(PropertyId)
        REFERENCES exp.PropertyDescriptor(PropertyId);

DROP INDEX exp.IDX_ObjectProperty_StringValue;

ALTER TABLE exp.ObjectProperty ALTER COLUMN StringValue TYPE VARCHAR(4000);

UPDATE exp.ObjectProperty SET StringValue=CAST(TextValue AS VARCHAR(4000)), TypeTag='s'
    WHERE StringValue IS NULL AND (TextValue IS NOT NULL OR TypeTag='t');

ALTER TABLE exp.ObjectProperty DROP COLUMN TextValue;

UPDATE exp.Material SET CpasType='Material' WHERE CpasType IS NULL;

SELECT core.fn_dropifexists ('Experiment', 'exp', 'INDEX', 'IX_Experiment_Container');
SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_CL_ExperimentRun_Container');
SELECT core.fn_dropifexists ('ExperimentRun', 'exp', 'INDEX', 'IX_ExperimentRun_ProtocolLSID');
SELECT core.fn_dropifexists ('RunList', 'exp', 'INDEX', 'IX_RunList_ExperimentRunId');
SELECT core.fn_dropifexists ('ProtocolApplicationParameter', 'exp', 'INDEX', 'IX_ProtocolApplicationParameter_AppId');
SELECT core.fn_dropifexists ('ProtocolActionPredecessor', 'exp', 'INDEX', 'IX_ProtocolActionPredecessor_PredecessorId');
SELECT core.fn_dropifexists ('ProtocolAction', 'exp', 'INDEX', 'IX_ProtocolAction_ChildProtocolId');
SELECT core.fn_dropifexists ('ProtocolParameter', 'exp', 'INDEX', 'IX_ProtocolParameter_ProtocolId');
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_Container');
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_SourceApplicationId');
SELECT core.fn_dropifexists ('Data', 'exp', 'INDEX', 'IX_Data_DataFileUrl');
SELECT core.fn_dropifexists ('DataInput', 'exp', 'INDEX', 'IX_DataInput_PropertyId');
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_Container');
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_SourceApplicationId');
SELECT core.fn_dropifexists ('Material', 'exp', 'INDEX', 'IX_Material_CpasType');
SELECT core.fn_dropifexists ('MaterialInput', 'exp', 'INDEX', 'IX_MaterialInput_PropertyId');
SELECT core.fn_dropifexists ('MaterialSource', 'exp', 'INDEX', 'IX_MaterialSource_Container');
SELECT core.fn_dropifexists ('ActiveMaterialSource', 'exp', 'INDEX', 'IX_ActiveMaterialSource_MaterialSourceLSID');
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject');
SELECT core.fn_dropifexists ('PropertyDomain', 'exp', 'INDEX', 'IX_PropertyDomain_DomainId');
SELECT core.fn_dropifexists ('PropertyDescriptor', 'exp', 'INDEX', 'IX_PropertyDescriptor_Container');
SELECT core.fn_dropifexists ('DomainDescriptor', 'exp', 'INDEX', 'IX_DomainDescriptor_Container');
SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_OwnerObjectId');

-- redundant to unique constraint
SELECT core.fn_dropifexists ('Object', 'exp', 'INDEX', 'IX_Object_Uri');

CREATE INDEX IX_Experiment_Container ON exp.Experiment(Container);
CREATE INDEX IX_CL_ExperimentRun_Container ON exp.ExperimentRun(Container);
CREATE INDEX IX_ExperimentRun_ProtocolLSID ON exp.ExperimentRun(ProtocolLSID);
CREATE INDEX IX_RunList_ExperimentRunId ON exp.RunList(ExperimentRunId);
CREATE INDEX IX_ProtocolApplicationParameter_AppId ON exp.ProtocolApplicationParameter(ProtocolApplicationId);
CREATE INDEX IX_ProtocolActionPredecessor_PredecessorId ON exp.ProtocolActionPredecessor(PredecessorId);
CREATE INDEX IX_ProtocolAction_ChildProtocolId ON exp.ProtocolAction(ChildProtocolId);
CREATE INDEX IX_ProtocolParameter_ProtocolId ON exp.ProtocolParameter(ProtocolId);
CREATE INDEX IX_Data_Container ON exp.Data(Container);
CREATE INDEX IX_Data_SourceApplicationId ON exp.Data(SourceApplicationId);
CREATE INDEX IX_DataInput_PropertyId ON exp.DataInput(PropertyId);
CREATE INDEX IX_Data_DataFileUrl ON exp.Data(DataFileUrl);
CREATE INDEX IX_Material_Container ON exp.Material(Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.Material(SourceApplicationId);
CREATE INDEX IX_MaterialInput_PropertyId ON exp.MaterialInput(PropertyId);
CREATE INDEX IX_Material_CpasType ON exp.Material(CpasType);
CREATE INDEX IX_MaterialSource_Container ON exp.MaterialSource(Container);
CREATE INDEX IX_ActiveMaterialSource_MaterialSourceLSID ON exp.ActiveMaterialSource(MaterialSourceLSID);
CREATE INDEX IX_ObjectProperty_PropertyObject ON exp.ObjectProperty(PropertyId, ObjectId);
CREATE INDEX IX_PropertyDomain_DomainId ON exp.PropertyDomain(DomainID);
CREATE INDEX IX_PropertyDescriptor_Container ON exp.PropertyDescriptor(Container);
CREATE INDEX IX_DomainDescriptor_Container ON exp.DomainDescriptor(Container);
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object(OwnerObjectId);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupContainer ENTITYID;
ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupSchema VARCHAR(50);
ALTER TABLE exp.PropertyDescriptor ADD COLUMN LookupQuery VARCHAR(50);

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

SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IDX_ObjectProperty_FloatValue');
SELECT core.fn_dropifexists ('ObjectProperty', 'exp', 'INDEX', 'IX_ObjectProperty_PropertyObject');

CREATE INDEX IDX_ObjectProperty_PropertyId ON exp.ObjectProperty(PropertyId);

ALTER TABLE exp.list ADD COLUMN TitleColumn VARCHAR(200) NULL;

ALTER TABLE exp.materialsource DROP COLUMN urlpattern;

ALTER TABLE exp.materialsource ADD COLUMN IdCol1 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol2 VARCHAR(200) NULL;
ALTER TABLE exp.materialsource ADD COLUMN IdCol3 VARCHAR(200) NULL;

ALTER TABLE exp.list
    ADD COLUMN DiscussionSetting SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN AllowDelete BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN AllowUpload BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN AllowExport BOOLEAN NOT NULL DEFAULT TRUE;

-- Used to attach discussions to lists
ALTER TABLE exp.IndexInteger
    ADD COLUMN EntityId ENTITYID;

ALTER TABLE exp.IndexVarchar
    ADD COLUMN EntityId ENTITYID;

ALTER TABLE exp.ExperimentRun ALTER COLUMN Name TYPE VARCHAR(100);

-- Fix up sample sets that were incorrectly created in a folder other than their domain
-- https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=4976

UPDATE exp.materialsource SET container =
    (SELECT MIN(dd.container) FROM exp.domaindescriptor dd WHERE exp.materialsource.lsid = dd.domainuri)
    WHERE rowid IN (SELECT ms.rowid FROM exp.materialsource ms, exp.domaindescriptor dd WHERE dd.domainuri = ms.lsid AND ms.container != dd.container);

-- Convert "FileLink" URIs to "Attachment" URIs in Lists
UPDATE exp.PropertyDescriptor SET RangeURI = 'http://www.labkey.org/exp/xml#attachment' WHERE PropertyId IN
(
    SELECT DISTINCT op.PropertyId FROM (SELECT ObjectId FROM exp.IndexInteger UNION SELECT ObjectId FROM exp.IndexVarchar) i INNER JOIN
        exp.ObjectProperty op ON i.ObjectId = op.ObjectId INNER JOIN
        exp.PropertyDescriptor pd ON op.PropertyId = pd.PropertyId
    WHERE RangeURI = 'http://cpas.fhcrc.org/exp/xml#fileLink'
);

CREATE INDEX IDX_Material_LSID ON exp.Material(LSID);

-- Clean up duplicate PropertyDescriptor and DomainDescriptors. Two cases:
-- 1. Assay definitions that were deleted before we correctly deleted the domains for the batch, run, and data sets.
-- 2. Duplicate input role domains, cause unknown. At least after the UNIQUE constraints are in place we'll find out if we try to insert dupes again

CREATE TEMPORARY TABLE PropertyIdsToDelete (PropertyId INT);

-- Grab the PropertyIds for properties that belong to assay domains where the assay has been deleted and we have a dupe
INSERT INTO PropertyIdsToDelete (SELECT p.propertyid FROM exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.domainid IN
        (SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
            AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
            AND domainuri NOT IN
            (SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%')));

-- Grab the PropertyIds for duplicate input role domains. We want all the DomainIds except the MAX ones for each DomainURI
INSERT INTO PropertyIdsToDelete (SELECT p.propertyid FROM exp.propertydescriptor p, exp.propertydomain pd WHERE p.propertyid = pd.propertyid AND pd.DomainId IN
    (SELECT DomainId FROM
        exp.DomainDescriptor dd,
        (SELECT COUNT(DomainURI) AS c, MAX(DomainId) AS m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
    WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND pd.DomainId NOT IN
    (SELECT MAX(DomainId) AS m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI));

-- Get rid of lingering uses of these orphaned PropertyDescriptors
DELETE FROM exp.ObjectProperty WHERE PropertyId IN (SELECT PropertyId FROM PropertyIdsToDelete);

-- Get rid of the duplicate PropertyDescriptors
DELETE FROM exp.PropertyDomain WHERE PropertyId IN (SELECT PropertyId FROM PropertyIdsToDelete);
DELETE FROM exp.PropertyDescriptor WHERE PropertyId IN (SELECT PropertyId FROM PropertyIdsToDelete);

DROP TABLE PropertyIdsToDelete;

-- Get rid of the orphaned assay domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN (SELECT domainid FROM exp.domaindescriptor WHERE domainuri LIKE '%:AssayDomain-%'
    AND domainid IN (SELECT DomainId FROM exp.DomainDescriptor WHERE DomainURI IN (SELECT DomainURI FROM (SELECT Count(DomainURI) AS c, DomainURI FROM exp.DomainDescriptor GROUP BY DomainURI) X WHERE c > 1))
    AND domainuri NOT IN
    (SELECT StringValue FROM exp.ObjectProperty op, exp.object o, exp.protocol p WHERE p.lsid = o.objecturi AND op.objectid = o.objectid AND StringValue LIKE '%:AssayDomain-%'));

-- Get rid of the duplicate input role domains
DELETE FROM exp.DomainDescriptor WHERE DomainId IN
    (SELECT DomainId FROM
        exp.DomainDescriptor dd,
        (SELECT COUNT(DomainURI) AS c, MAX(DomainId) AS m, DomainURI FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI) x
    WHERE dd.DomainURI = x.DomainURI AND x.c > 1)
AND DomainId NOT IN
    (SELECT MAX(DomainId) AS m FROM exp.DomainDescriptor WHERE DomainURI LIKE '%:DataInputRole' OR DomainURI LIKE '%:MaterialInputRole' GROUP BY DomainURI);

-- Add the contraints
ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyURIContainer UNIQUE (PropertyURI, Container);
ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainURIContainer UNIQUE (DomainURI, Container);

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

-- Change StringValue from VARCHAR(4000) to TEXT
ALTER TABLE exp.ProtocolApplicationParameter ALTER COLUMN StringValue TYPE TEXT;

UPDATE exp.protocolapplication SET cpastype = 'ProtocolApplication' WHERE
    cpastype != 'ProtocolApplication' AND
    cpastype != 'ExperimentRun' AND
    cpastype != 'ExperimentRunOutput';

/* exp-8.30-9.10.sql */

-- Migrate from storing roles as property descriptors to storing them as strings on the MaterialInput
ALTER TABLE exp.MaterialInput ADD COLUMN Role VARCHAR(50);
UPDATE exp.MaterialInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.MaterialInput.PropertyId);
UPDATE exp.MaterialInput SET Role = 'Material' WHERE Role IS NULL;
ALTER TABLE exp.MaterialInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_MaterialInput_Role ON exp.MaterialInput(Role);

ALTER TABLE exp.MaterialInput DROP COLUMN PropertyId;

-- Migrate from storing roles as property descriptors to storing them as strings on the DataInput
ALTER TABLE exp.DataInput ADD COLUMN Role VARCHAR(50);
UPDATE exp.DataInput SET Role =
    (SELECT pd.Name FROM exp.PropertyDescriptor pd WHERE pd.PropertyId = exp.DataInput.PropertyId);
UPDATE exp.DataInput SET Role = 'Data' WHERE Role IS NULL;
ALTER TABLE exp.DataInput ALTER COLUMN Role SET NOT NULL;
CREATE INDEX IDX_DataInput_Role ON exp.DataInput(Role);

ALTER TABLE exp.DataInput DROP COLUMN PropertyId;

-- Clean up the domain and property descriptors for input roles
DELETE FROM exp.PropertyDomain WHERE
    DomainId IN (SELECT DomainId FROM exp.DomainDescriptor WHERE
                    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
                    DomainURI LIKE '%:Domain.Folder-%:DataInputRole')
    OR PropertyId IN (SELECT PropertyId FROM exp.PropertyDescriptor WHERE
                    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
                    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%');

DELETE FROM exp.DomainDescriptor WHERE
    DomainURI LIKE '%:Domain.Folder-%:MaterialInputRole' OR
    DomainURI LIKE '%:Domain.Folder-%:DataInputRole';

DELETE FROM exp.PropertyDescriptor WHERE
    PropertyURI LIKE '%:Domain.Folder-%:MaterialInputRole#%' OR
    PropertyURI LIKE '%:Domain.Folder-%:DataInputRole#%';

ALTER TABLE exp.Experiment ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.ObjectProperty ADD COLUMN QcValue VARCHAR(50);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN QcEnabled BOOLEAN NOT NULL DEFAULT '0';

ALTER TABLE exp.materialsource ADD COLUMN ParentCol VARCHAR(200) NULL;

ALTER TABLE exp.Experiment ADD COLUMN
    BatchProtocolId INT NULL;

ALTER TABLE exp.Experiment ADD CONSTRAINT
    FK_Experiment_BatchProtocolId FOREIGN KEY (BatchProtocolId) REFERENCES exp.Protocol (RowId);

CREATE INDEX IDX_Experiment_BatchProtocolId ON exp.Experiment(BatchProtocolId);

ALTER TABLE exp.PropertyDescriptor ADD DefaultValueType VARCHAR(50);

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
);

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
);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN Hidden BOOLEAN NOT NULL DEFAULT '0';

/* exp-9.10-9.20.sql */

ALTER TABLE exp.Data DROP COLUMN SourceProtocolLSID;

ALTER TABLE exp.Material DROP COLUMN SourceProtocolLSID;

ALTER TABLE exp.ObjectProperty
    RENAME COLUMN QcValue TO MvIndicator;

ALTER TABLE exp.PropertyDescriptor ADD COLUMN MvEnabled BOOLEAN NOT NULL DEFAULT '0';

UPDATE exp.PropertyDescriptor SET MvEnabled = QcEnabled;

ALTER TABLE exp.PropertyDescriptor DROP COLUMN QcEnabled;

/* exp-9.20-9.30.sql */

ALTER TABLE exp.PropertyDomain ADD COLUMN SortOrder INT NOT NULL DEFAULT 0;

-- Set the initial sort to be the same as it has thus far
UPDATE exp.PropertyDomain SET SortOrder = PropertyId;

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ImportAliases VARCHAR(200);

ALTER TABLE exp.PropertyDescriptor ADD COLUMN URL VARCHAR(200);

-- remove property validator dependency on LSID authority
UPDATE exp.propertyvalidator SET typeuri = 'urn:lsid:labkey.com:' ||
    (CASE WHEN strpos(typeuri, 'PropertyValidator:range') = 0
        THEN 'PropertyValidator:regex'
        ELSE 'PropertyValidator:range' END)
  WHERE typeuri NOT LIKE 'urn:lsid:labkey.com:%';

ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInInsertView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInUpdateView BOOLEAN NOT NULL DEFAULT '1';
ALTER TABLE exp.PropertyDescriptor ADD COLUMN ShownInDetailsView BOOLEAN NOT NULL DEFAULT '1';

/* exp-9.30-10.10.sql */

ALTER TABLE exp.Data ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Data ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Data ADD COLUMN Modified TIMESTAMP;

UPDATE exp.Data SET Modified = Created;

UPDATE exp.Data SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Data.RunId);


ALTER TABLE exp.Material ADD COLUMN CreatedBy INT;
ALTER TABLE exp.Material ADD COLUMN ModifiedBy INT;
ALTER TABLE exp.Material ADD COLUMN Modified TIMESTAMP;

UPDATE exp.Material SET Modified = Created;

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.ExperimentRun WHERE exp.ExperimentRun.RowId = exp.Material.RunId);

UPDATE exp.Material SET CreatedBy =
  (SELECT CreatedBy FROM exp.MaterialSource WHERE exp.MaterialSource.LSID = exp.Material.CpasType)
  WHERE CreatedBy IS NULL;

/* exp-10.10-10.20.sql */

ALTER TABLE exp.list
    ADD COLUMN IndexMetaData BOOLEAN NOT NULL DEFAULT TRUE;
