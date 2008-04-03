/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
-- Steps in this script:
-- 1.  Find and delete orphan records in the exp schema.  
--	several views are created then later dropped to simplify the sql to identify orphans
-- 	Make the container column not null to prevent one type of orphan
-- 2.  Upgrade the tables underlying the exp OntologyManager.  
--      PropertyDescriptor is altered
--  	Property is replaced by Objects and ObjectProperties
--	Stored procs/functions to add/drop property entries
-- 3.  Copy old property  entries into new tables
-- 4.  Drop the old property table


-- The purpose of these views is to find and delete "orphan" experiment objects, for example
-- ExperimentRuns whose container has been deleted but are still in the database.  Orphans are
-- evidence of a bug in the CPAS system at some point in the past; the current Experiment module 
-- should not be orphaning records anymore.
-- 

CREATE OR REPLACE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
;

CREATE OR REPLACE VIEW exp._orphanProtocolView AS
SELECT * FROM exp.Protocol WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanExperimentView AS
SELECT * FROM exp.Experiment WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanExperimentRunView AS
SELECT * FROM exp.ExperimentRun WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanProtocolApplicationView AS
SELECT * FROM exp.ProtocolApplication WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView))
;
CREATE OR REPLACE VIEW exp._orphanMaterialView AS
SELECT * FROM exp.Material WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE OR REPLACE VIEW exp._orphanDataView AS
SELECT * FROM exp.Data WHERE (runid IN (SELECT rowid FROM exp._orphanExperimentRunView)) OR 
	(runid IS NULL AND container NOT IN (SELECT entityid FROM core.containers)) OR 
	(container IS NULL)
;
CREATE OR REPLACE VIEW exp._orphanMaterialSourceView AS
SELECT * FROM exp.MaterialSource WHERE container NOT IN (SELECT entityid FROM core.containers) OR container IS NULL 
;
CREATE OR REPLACE VIEW exp._orphanLSIDView AS
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanProtocolView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentView  UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanExperimentRunView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanDataView UNION
SELECT LSID, 'n/a' AS Container FROM exp._orphanProtocolApplicationView UNION
SELECT LSID, CAST (Container AS varchar) AS Container FROM exp._orphanMaterialSourceView 
;
CREATE OR REPLACE VIEW exp.AllLsidContainers AS
	SELECT LSID, Container, 'Protocol' AS Type FROM exp.Protocol UNION ALL
	SELECT exp.ProtocolApplication.LSID, Container, 'ProtocolApplication' AS Type FROM exp.ProtocolApplication JOIN exp.Protocol ON exp.Protocol.LSID = exp.ProtocolApplication.ProtocolLSID UNION ALL
	SELECT LSID, Container, 'Experiment' AS Type FROM exp.Experiment UNION ALL
	SELECT LSID, Container, 'Material' AS Type FROM exp.Material UNION ALL
	SELECT LSID, Container, 'MaterialSource' AS Type FROM exp.MaterialSource UNION ALL
	SELECT LSID, Container, 'Data' AS Type FROM exp.Data UNION ALL
	SELECT LSID, Container, 'ExperimentRun' AS Type FROM exp.ExperimentRun
;

-- delete the orphans identified by the Views

DELETE FROM exp.DataInput WHERE 
	(dataid IN (SELECT rowid FROM exp._orphanDataView )) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.MaterialInput WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) OR 
	(targetapplicationid IN	(SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.Fraction WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 
;
DELETE FROM exp.biosource WHERE 
	(materialid IN (SELECT rowid FROM exp._orphanMaterialView)) 
;
DELETE FROM exp.Data WHERE rowid IN (SELECT rowid FROM exp._orphanDataView)
;
DELETE FROM exp.Material WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialView)
;
DELETE FROM exp.protocolapplicationparameter WHERE 
	(ProtocolApplicationId IN (SELECT rowid FROM exp._orphanProtocolApplicationView))
;
DELETE FROM exp.ProtocolApplication WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolApplicationView)
;
DELETE FROM exp.MaterialSource WHERE rowid IN (SELECT rowid FROM exp._orphanMaterialSourceView)
;
DELETE FROM exp.ExperimentRun WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentRunView)
;
DELETE FROM exp.protocolactionpredecessor WHERE 
	(actionid IN (SELECT rowid FROM exp.protocolaction WHERE parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView)))
;
DELETE FROM exp.protocolaction WHERE 
	(parentprotocolid IN (SELECT rowid FROM exp._orphanProtocolView))
;
DELETE FROM exp.protocolparameter WHERE 
	(protocolid IN (SELECT rowid FROM exp._orphanProtocolView))
;
DELETE FROM exp.Protocol WHERE rowid IN (SELECT rowid FROM exp._orphanProtocolView)
;
DELETE FROM exp.Experiment WHERE rowid IN (SELECT rowid FROM exp._orphanExperimentView)
;
DELETE FROM exp.property WHERE 
	(parentURI IN (SELECT LSID FROM exp._orphanLSIDView )) OR
	(parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers)
		AND parentURI NOT IN
			(SELECT PropertyURIValue FROM exp.Property
			WHERE parentURI NOT IN (SELECT lsid FROM exp.AllLsidContainers)))
;
-- now make the container fields not null
ALTER TABLE exp.Experiment 
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.ExperimentRun 
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Data
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Material
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.MaterialSource
	ALTER COLUMN Container SET NOT NULL;
ALTER TABLE exp.Protocol
	ALTER COLUMN Container SET NOT NULL;

-- now drop the views
DROP VIEW exp._orphanLSIDView 
;
DROP VIEW exp._orphanMaterialSourceView 
;
DROP VIEW exp._orphanDataView 
;
DROP VIEW exp._orphanMaterialView 
;
DROP VIEW exp._orphanProtocolApplicationView 
;
DROP VIEW exp._orphanExperimentRunView 
;
DROP VIEW exp._orphanExperimentView 
;
DROP VIEW exp._orphanProtocolView 
;

-- now create/modify the property tables

--
-- PropertyDescriptor
--


-- TODO: use M-M junction table for PropertyDescriptor <-> ObjectType
-- I'd like to rename RowId to PropertyId

ALTER TABLE exp.PropertyDescriptor ADD DatatypeURI varchar(200) NOT NULL DEFAULT 'http://www.w3.org/2001/XMLSchema#string'
;


--
-- Object
--

-- DROP TABLE exp.ObjectProperty
-- DROP TABLE exp.Object

CREATE TABLE exp.Object
	(
	ObjectId SERIAL NOT NULL,
	Container ENTITYID NOT NULL,
	ObjectURI LSIDType NOT NULL,
	OwnerObjectId int NULL
	)
;


ALTER TABLE exp.Object ADD CONSTRAINT PK_Object PRIMARY KEY (ObjectId);
ALTER TABLE exp.Object ADD CONSTRAINT UQ_Object UNIQUE (Container, ObjectURI);
CREATE INDEX IDX_Object_OwnerObjectId ON exp.Object (OwnerObjectId);
-- CONSIDER: CONSTRAINT (Container, OwnerObjectId) --> (Container, ObjectId)
ALTER TABLE exp.Object ADD
	CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId)
		REFERENCES exp.Object (ObjectId)
;

--
-- ObjectProperty
--


CREATE TABLE exp.ObjectProperty
	(
	ObjectId int NOT NULL,  -- FK exp.Object
	PropertyId int NOT NULL, -- FK exp.PropertyDescriptor
	TypeTag char(1) NOT NULL, -- s string, f float, d datetime, t text
	FloatValue float NULL,
	DateTimeValue timestamp NULL,
	StringValue varchar(400) NULL,
	TextValue text NULL
	)
;


ALTER TABLE exp.ObjectProperty
   ADD CONSTRAINT PK_ObjectProperty PRIMARY KEY (ObjectId, PropertyId);
ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId)
		REFERENCES exp.Object (ObjectId);
ALTER TABLE exp.ObjectProperty ADD
	CONSTRAINT FK_ObjectProperty_PropertyDescriptor FOREIGN KEY (PropertyId)
		REFERENCES exp.PropertyDescriptor (RowId)
;



CREATE OR REPLACE VIEW exp.ObjectPropertiesView AS
	SELECT
		O.*,
		PD.name, PD.PropertyURI, PD.DatatypeURI,
		P.TypeTag, P.FloatValue, P.StringValue, P.DatetimeValue, P.TextValue
	FROM exp.ObjectProperty P JOIN exp.Object O ON P.ObjectId = O.ObjectId JOIN exp.PropertyDescriptor PD ON P.PropertyID = PD.RowId
;



CREATE OR REPLACE FUNCTION exp.ensureObject(ENTITYID, LSIDType, INTEGER) RETURNS INTEGER AS '
DECLARE
	_container ALIAS FOR $1;
	_lsid ALIAS FOR $2;
	_ownerObjectId ALIAS FOR $3;
	_objectid INTEGER;
BEGIN
--	START TRANSACTION;
		_objectid := (SELECT ObjectId FROM exp.Object where Container=_container AND ObjectURI=_lsid);
		IF (_objectid IS NULL) THEN
			INSERT INTO exp.Object (Container, ObjectURI, OwnerObjectId) VALUES (_container, _lsid, _ownerObjectId);
			_objectid := currval(\'exp.object_objectid_seq\');
		END IF;
--	COMMIT;
	RETURN _objectid;
END;
' LANGUAGE plpgsql;

-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA',null)
-- SELECT * FROM exp.ObjectPropertiesView


CREATE OR REPLACE FUNCTION exp.deleteObject(ENTITYID, LSIDType) RETURNS void AS '
DECLARE
	_container ALIAS FOR $1;
	_lsid ALIAS FOR $2;
	_objectid INTEGER;
BEGIN
		_objectid := (SELECT ObjectId FROM exp.Object where Container=_container AND ObjectURI=_lsid);
		IF (_objectid IS NULL) THEN 
			RETURN;
		END IF;
--	START TRANSACTION;
		DELETE FROM exp.ObjectProperty WHERE ObjectId IN 
			(SELECT ObjectId FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid);
		DELETE FROM exp.ObjectProperty WHERE ObjectId = _objectid;
		DELETE FROM exp.Object WHERE Container=_container AND OwnerObjectId = _objectid;
		DELETE FROM exp.Object WHERE ObjectId = _objectid;
--	COMMIT;
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.deleteObject('00000000-0000-0000-0000-000000000000', 'lsidA')
-- SELECT * FROM exp.ObjectPropertiesView


--
-- This is the most general set property method
--


CREATE OR REPLACE FUNCTION exp.setProperty(INTEGER, LSIDType, LSIDType, CHAR(1), FLOAT, varchar(400), timestamp, TEXT) RETURNS void AS '
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
--	START TRANSACTION;
		_propertyid := (SELECT RowId FROM exp.PropertyDescriptor WHERE PropertyURI=_propertyuri);
		if (1=1 OR _propertyid IS NULL) THEN
			INSERT INTO exp.PropertyDescriptor (PropertyURI, DatatypeURI) VALUES (_propertyuri, _datatypeuri);
			_propertyid := currval(\'exp.propertydescriptor_rowid_seq\');
		END IF;
		DELETE FROM exp.ObjectProperty WHERE ObjectId=_objectid AND PropertyId=_propertyid;
		INSERT INTO exp.ObjectProperty (ObjectId, PropertyID, TypeTag, FloatValue, StringValue, DateTimeValue, TextValue)
			VALUES (_objectid, _propertyid, _tag, _f, _s, _d, _t);
--	COMMIT;
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.setProperty(13, 'lsidPROP', 'lsidTYPE', 'f', 1.0, null, null, null)
-- SELECT * FROM exp.ObjectPropertiesView

-- internal methods


CREATE OR REPLACE FUNCTION exp._insertFloatProperty(INTEGER, INTEGER, FLOAT) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_float ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
	VALUES (_objectid, _propid, \'f\', _float);
	RETURN;
END;
' LANGUAGE plpgsql;

-- SELECT exp._insertFloatProperty(13, 5, 101.0)


CREATE OR REPLACE FUNCTION exp._insertDateTimeProperty(INTEGER, INTEGER, TIMESTAMP) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_datetime ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
	VALUES (_objectid, _propid, \'d\', _datetime);
	RETURN;
END;
' LANGUAGE plpgsql;



CREATE OR REPLACE FUNCTION exp._insertStringProperty(INTEGER, INTEGER, VARCHAR(400)) RETURNS void AS '
DECLARE
	_objectid ALIAS FOR $1;
	_propid ALIAS FOR $2;
	_string ALIAS FOR $3;
BEGIN
	IF (_propid IS NULL OR _objectid IS NULL)  THEN
		RETURN;
	END IF;
	INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, StringValue)
	VALUES (_objectid, _propid, \'s\', _string);
	RETURN;
END;
' LANGUAGE plpgsql;



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
--	BEGIN TRANSACTION
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
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;


-- SELECT exp.setFloatProperties(4, 13, 100.0, 14, 101.0, 15, 102.0, 16, 104.0, null, null, null, null, null, null, null, null, null, null, null, null)
-- SELECT * FROM exp.Object
-- SELECT * FROM exp.PropertyDescriptor
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidB',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidC',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidD',null)
-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidE',null)


CREATE OR REPLACE FUNCTION exp.setStringProperties(_propertyid INTEGER,
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
	) RETURNS void AS '
BEGIN
--	BEGIN TRANSACTION
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
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.setDateTimeProperties(_propertyid INTEGER,
	_objectid1 INTEGER, _datetime1 timestamp,
	_objectid2 INTEGER, _datetime2 timestamp,
	_objectid3 INTEGER, _datetime3 timestamp,
	_objectid4 INTEGER, _datetime4 timestamp,
	_objectid5 INTEGER, _datetime5 timestamp,
	_objectid6 INTEGER, _datetime6 timestamp,
	_objectid7 INTEGER, _datetime7 timestamp,
	_objectid8 INTEGER, _datetime8 timestamp,
	_objectid9 INTEGER, _datetime9 timestamp,
	_objectid10 INTEGER, _datetime10 timestamp
	) RETURNS void AS '
BEGIN
--	BEGIN TRANSACTION
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
--	COMMIT
	RETURN;
END;
' LANGUAGE plpgsql;



--
-- Migrate data
--


-- Experiment Objects (need to fix up container)

INSERT INTO exp.Object (Container, ObjectURI)
SELECT DISTINCT '00000000-0000-0000-0000-000000000000', ParentURI
FROM exp.Property
;

-- PropertyDescriptors


INSERT INTO exp.PropertyDescriptor (PropertyURI, ValueType, Name)
SELECT OntologyEntryURI, MAX(ValueType), MAX(Name)
FROM exp.Property
WHERE OntologyEntryURI NOT IN (SELECT PropertyURI FROM exp.PropertyDescriptor)
GROUP BY OntologyEntryURI
;


UPDATE exp.PropertyDescriptor SET DatatypeURI =
	CASE
		WHEN ValueType = 'String'      THEN 'http://www.w3.org/2001/XMLSchema#string'
		WHEN ValueType = 'PropertyURI' THEN 'http://www.w3.org/2000/01/rdf-schema#Resource'
		WHEN ValueType = 'Integer'     THEN 'http://www.w3.org/2001/XMLSchema#int'
		WHEN ValueType = 'FileLink'    THEN 'http://cpas.fhcrc.org/exp/xml#fileLink'
		WHEN ValueType = 'DateTime'    THEN 'http://www.w3.org/2001/XMLSchema#dateTime'
		WHEN ValueType = 'Double'      THEN 'http://www.w3.org/2001/XMLSchema#double'
		WHEN ValueType = 'XmlText'     THEN 'http://cpas.fhcrc.org/exp/document#text-xml'
	END
WHERE ValueType IN ('String', 'PropertyURI', 'Integer', 'FileLink', 'DateTime', 'Double', 'XmlText')
;


-- Properties

INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue, DateTimeValue, StringValue, TextValue)
SELECT O.ObjectId, PD.RowId,
	CASE
		WHEN P.ValueType = 'String' THEN 's'
		WHEN P.ValueType = 'PropertyURI' THEN 's'
		WHEN P.ValueType = 'Integer' THEN 'f'
		WHEN P.ValueType = 'FileLink' THEN 's'
		WHEN P.ValueType = 'DateTime' THEN 'd'
		WHEN P.ValueType = 'Double' THEN 'f'
		WHEN P.ValueType = 'XmlText' THEN 't'
	END,
	CASE WHEN IntegerValue IS NOT NULL THEN IntegerValue ELSE DoubleValue END,
	DateTimeValue, -- DateTime
	CASE WHEN StringValue IS NOT NULL THEN StringValue WHEN PropertyURIValue IS NOT NULL THEN PropertyURIValue ELSE FileLinkValue END,
	XmlTextValue -- Text
FROM exp.Property P INNER JOIN exp.Object O on P.ParentURI = O.ObjectURI INNER JOIN exp.PropertyDescriptor PD on P.OntologyEntryURI = PD.PropertyURI
;

--
-- fix-up Containers in the Objects table
--

-- find object with same LSID and update container
UPDATE exp.Object
SET Container = (SELECT Container FROM exp.AllLsidContainers WHERE Lsid = ObjectURI)
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000' AND EXISTS (SELECT Container FROM exp.AllLsidContainers WHERE Lsid = ObjectURI)
;

-- find children and udpate container
UPDATE exp.Object 
SET Container = (SELECT PARENT.Container FROM exp.ObjectProperty OP INNER JOIN exp.Object PARENT ON OP.ObjectId = Parent.ObjectID WHERE OP.StringValue = ObjectURI)
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000' AND EXISTS (SELECT PARENT.Container FROM exp.ObjectProperty OP INNER JOIN exp.Object PARENT ON OP.ObjectId = Parent.ObjectID WHERE OP.StringValue = ObjectURI)
;

-- find children and udpate container
UPDATE exp.Object 
SET Container = (SELECT PARENT.Container FROM exp.ObjectProperty OP INNER JOIN exp.Object PARENT ON OP.ObjectId = Parent.ObjectID WHERE OP.StringValue = ObjectURI)
WHERE exp.Object.Container = '00000000-0000-0000-0000-000000000000' AND EXISTS (SELECT PARENT.Container FROM exp.ObjectProperty OP INNER JOIN exp.Object PARENT ON OP.ObjectId = Parent.ObjectID WHERE OP.StringValue = ObjectURI)
;

SELECT COUNT(*)
FROM exp.Object
WHERE Container = '00000000-0000-0000-0000-000000000000'
;


--
-- fix up OwnerObjectId
--

UPDATE exp.Object
SET OwnerObjectId = (SELECT MAX(OWNER.ObjectId)
		FROM exp.Property PROPS JOIN exp.ExperimentRun RUN on PROPS.RunId = RUN.RowId JOIN exp.Object OWNER ON RUN.Lsid = OWNER.ObjectURI
		WHERE PROPS.ParentURI = exp.Object.ObjectURI)
;
-- now drop the old proerty table
DROP TABLE exp.Property;
