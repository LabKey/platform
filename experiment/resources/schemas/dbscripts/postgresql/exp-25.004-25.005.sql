/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- @LongRunningScript('updating all ObjectId columns to BIGINT')

-- Change all ObjectId columns to BIGINT
ALTER TABLE exp.Object ALTER COLUMN ObjectId TYPE BIGINT;
ALTER TABLE exp.Object ALTER COLUMN OwnerObjectId TYPE BIGINT;
ALTER TABLE exp.ObjectProperty ALTER COLUMN ObjectId TYPE BIGINT;
ALTER TABLE exp.Edge ALTER COLUMN FromObjectId TYPE BIGINT;
ALTER TABLE exp.Edge ALTER COLUMN ToObjectId TYPE BIGINT;
ALTER TABLE exp.Edge ALTER COLUMN SourceId TYPE BIGINT;
ALTER TABLE exp.Data ALTER COLUMN ObjectId TYPE BIGINT;
ALTER TABLE exp.ExperimentRun ALTER COLUMN ObjectId TYPE BIGINT;
ALTER TABLE exp.Material ALTER COLUMN ObjectId TYPE BIGINT;
ALTER TABLE exp.ObjectLegacyNames ALTER COLUMN ObjectId TYPE BIGINT;

-- Change the auto-increment sequence to BIGINT
ALTER SEQUENCE exp.Object_ObjectId_Seq AS BIGINT;

-- Update all stored functions to work with BIGINT ObjectIds (and PropertyIds, just for good measure)

-- SELECT exp.ensureObject('00000000-0000-0000-0000-000000000000', 'lsidA', NULL)
CREATE OR REPLACE FUNCTION exp.ensureObject(_container ENTITYID, _lsid LSIDType, _ownerObjectId BIGINT) RETURNS BIGINT AS $$
DECLARE
    _objectid BIGINT;
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
CREATE OR REPLACE FUNCTION exp.deleteObject(_container ENTITYID, _lsid LSIDType) RETURNS void AS $$
DECLARE
    _objectid BIGINT;
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
$$ LANGUAGE plpgsql;


-- internal methods

-- SELECT exp._insertFloatProperty(13, 5, 101.0)
CREATE OR REPLACE FUNCTION exp._insertFloatProperty(_objectid BIGINT, _propid BIGINT, _float FLOAT) RETURNS void AS $$
BEGIN
    IF (_propid IS NULL OR _objectid IS NULL) THEN
        RETURN;
    END IF;
INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
VALUES (_objectid, _propid, 'f', _float);
RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp._insertDateTimeProperty(_objectid BIGINT, _propid BIGINT, _datetime TIMESTAMP) RETURNS void AS $$
BEGIN
    IF (_propid IS NULL OR _objectid IS NULL) THEN
        RETURN;
    END IF;
INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
VALUES (_objectid, _propid, 'd', _datetime);
RETURN;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp._insertStringProperty(_objectid BIGINT, _propid BIGINT, _string VARCHAR(400)) RETURNS void AS $$
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
-- Set the same property on multiple objects (e.g. import a column of data)
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
CREATE OR REPLACE FUNCTION exp.setFloatProperties(
    _propertyid BIGINT,
    _objectid1 BIGINT, _float1 FLOAT,
    _objectid2 BIGINT, _float2 FLOAT,
    _objectid3 BIGINT, _float3 FLOAT,
    _objectid4 BIGINT, _float4 FLOAT,
    _objectid5 BIGINT, _float5 FLOAT,
    _objectid6 BIGINT, _float6 FLOAT,
    _objectid7 BIGINT, _float7 FLOAT,
    _objectid8 BIGINT, _float8 FLOAT,
    _objectid9 BIGINT, _float9 FLOAT,
    _objectid10 BIGINT, _float10 FLOAT
) RETURNS void AS $$
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
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.setStringProperties
(
    _propertyid BIGINT,
    _objectid1 BIGINT, _string1 VARCHAR(400),
    _objectid2 BIGINT, _string2 VARCHAR(400),
    _objectid3 BIGINT, _string3 VARCHAR(400),
    _objectid4 BIGINT, _string4 VARCHAR(400),
    _objectid5 BIGINT, _string5 VARCHAR(400),
    _objectid6 BIGINT, _string6 VARCHAR(400),
    _objectid7 BIGINT, _string7 VARCHAR(400),
    _objectid8 BIGINT, _string8 VARCHAR(400),
    _objectid9 BIGINT, _string9 VARCHAR(400),
    _objectid10 BIGINT, _string10 VARCHAR(400)
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


CREATE OR REPLACE FUNCTION exp.setDateTimeProperties(
    _propertyid BIGINT,
    _objectid1 BIGINT, _datetime1 TIMESTAMP,
    _objectid2 BIGINT, _datetime2 TIMESTAMP,
    _objectid3 BIGINT, _datetime3 TIMESTAMP,
    _objectid4 BIGINT, _datetime4 TIMESTAMP,
    _objectid5 BIGINT, _datetime5 TIMESTAMP,
    _objectid6 BIGINT, _datetime6 TIMESTAMP,
    _objectid7 BIGINT, _datetime7 TIMESTAMP,
    _objectid8 BIGINT, _datetime8 TIMESTAMP,
    _objectid9 BIGINT, _datetime9 TIMESTAMP,
    _objectid10 BIGINT, _datetime10 TIMESTAMP
) RETURNS void AS $$
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
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION exp.deleteObjectById(_container ENTITYID, _inputObjectId BIGINT) RETURNS void AS $$
DECLARE
    _objectid BIGINT;
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
$$ LANGUAGE plpgsql;
