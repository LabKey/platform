/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
-- @LongRunningScript('updating all ObjectId columns to BIGINT')

-- Viability no longer supports SQL Server, but its old schema may have been left behind. Ensure it and its FK to ObjectId are dropped.
EXEC core.fn_dropifexists '*', 'viability', 'SCHEMA';

-- Drop foreign keys that involve ObjectIds
ALTER TABLE exp.Edge DROP CONSTRAINT FK_Edge_From_Object;
ALTER TABLE exp.Edge DROP CONSTRAINT FK_Edge_To_Object;
ALTER TABLE exp.Edge DROP CONSTRAINT FK_Edge_SourceId_Object;
ALTER TABLE exp.ExperimentRun DROP CONSTRAINT FK_ExperimentRun_ObjectId;
ALTER TABLE exp.Material DROP CONSTRAINT FK_Material_ObjectId;
ALTER TABLE exp.Data DROP CONSTRAINT FK_Data_ObjectId;
ALTER TABLE exp.ObjectProperty DROP CONSTRAINT FK_ObjectProperty_Object;
ALTER TABLE exp.Object DROP CONSTRAINT FK_Object_Object;

-- Drop other constraints that involve ObjectIds
ALTER TABLE exp.ObjectProperty DROP CONSTRAINT PK_ObjectProperty;
ALTER TABLE exp.Object DROP CONSTRAINT PK_Object;
ALTER TABLE exp.Edge DROP CONSTRAINT UQ_Edge_FromTo_RunId_SourceId_SourceKey;

-- Drop indexes that involve ObjectIds
DROP INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object;
DROP INDEX IX_Object_OwnerObjectId ON exp.Object;
DROP INDEX IX_Edge_ToObjectId ON exp.Edge;
DROP INDEX IX_Edge_SourceId ON exp.Edge;
DROP INDEX idx_data_objectid ON exp.Data;
DROP INDEX idx_experimentrun_objectid ON exp.ExperimentRun;
DROP INDEX idx_material_objectid ON exp.Material;

-- Change all ObjectId columns to BIGINT. Note: Unlike PostgreSQL, SQL Server does not maintain NOT NULL on INT -> BIGINT type changes.
ALTER TABLE exp.Object ALTER COLUMN ObjectId BIGINT NOT NULL;
ALTER TABLE exp.Object ALTER COLUMN OwnerObjectId BIGINT NULL;
ALTER TABLE exp.ObjectProperty ALTER COLUMN ObjectId BIGINT NOT NULL;
ALTER TABLE exp.Edge ALTER COLUMN FromObjectId BIGINT NOT NULL;
ALTER TABLE exp.Edge ALTER COLUMN ToObjectId BIGINT NOT NULL;
ALTER TABLE exp.Edge ALTER COLUMN SourceId BIGINT NULL;
ALTER TABLE exp.Data ALTER COLUMN ObjectId BIGINT NOT NULL;
ALTER TABLE exp.ExperimentRun ALTER COLUMN ObjectId BIGINT NOT NULL;
ALTER TABLE exp.Material ALTER COLUMN ObjectId BIGINT NOT NULL;
ALTER TABLE exp.ObjectLegacyNames ALTER COLUMN ObjectId BIGINT NOT NULL;

-- Recreate indexes
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid);
CREATE UNIQUE INDEX idx_experimentrun_objectid ON exp.experimentrun (objectid);
CREATE UNIQUE INDEX idx_data_objectid ON exp.data (objectid);
CREATE INDEX IX_Edge_SourceId ON exp.Edge (SourceId);
CREATE INDEX IX_Edge_ToObjectId ON exp.Edge (ToObjectId);
CREATE INDEX IX_Object_OwnerObjectId ON exp.Object (OwnerObjectId)
CREATE CLUSTERED INDEX IDX_Object_ContainerOwnerObjectId ON exp.Object (Container, OwnerObjectId, ObjectId);

-- Recreate constraints
ALTER TABLE exp.Edge ADD CONSTRAINT UQ_Edge_FromTo_RunId_SourceId_SourceKey UNIQUE (FromObjectId, ToObjectId, RunId, SourceId, SourceKey);
ALTER TABLE exp.Object ADD CONSTRAINT PK_Object PRIMARY KEY NONCLUSTERED (ObjectId);
ALTER TABLE exp.ObjectProperty ADD CONSTRAINT PK_ObjectProperty PRIMARY KEY CLUSTERED (ObjectId, PropertyId);

-- Recreate foreign keys
ALTER TABLE exp.Object ADD CONSTRAINT FK_Object_Object FOREIGN KEY (OwnerObjectId) REFERENCES exp.Object (ObjectId);
ALTER TABLE exp.ObjectProperty ADD CONSTRAINT FK_ObjectProperty_Object FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId);
ALTER TABLE exp.Data ADD CONSTRAINT FK_Data_ObjectId FOREIGN KEY (ObjectId) REFERENCES exp.Object (ObjectId);
ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_ObjectId FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.ExperimentRun ADD CONSTRAINT FK_ExperimentRun_ObjectId FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.Edge ADD CONSTRAINT FK_Edge_SourceId_Object FOREIGN KEY (SourceId) REFERENCES exp.Object (Objectid);
ALTER TABLE exp.Edge ADD CONSTRAINT FK_Edge_To_Object FOREIGN KEY (ToObjectId) REFERENCES exp.object (objectid);
ALTER TABLE exp.Edge ADD CONSTRAINT FK_Edge_From_Object FOREIGN KEY (FromObjectId) REFERENCES exp.object (objectid);

-- Update all stored functions to work with BIGINT ObjectIds (and PropertyIds, just for good measure)

-- This one is not used and not implemented on PostgreSQL, so drop it
DROP PROCEDURE exp.getObjectProperties;

GO

CREATE OR ALTER PROCEDURE exp.ensureObject(@container ENTITYID, @lsid LSIDType, @ownerObjectId BIGINT) AS
BEGIN
    DECLARE @objectid AS BIGINT
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
CREATE OR ALTER PROCEDURE exp.deleteObject(@container ENTITYID, @lsid LSIDType) AS
BEGIN
    SET NOCOUNT ON
    DECLARE @objectid BIGINT
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
-- internal methods
CREATE OR ALTER PROCEDURE exp._insertFloatProperty(@objectid BIGINT, @propid BIGINT, @float FLOAT) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, FloatValue)
    VALUES (@objectid, @propid, 'f', @float)
END

GO
CREATE OR ALTER PROCEDURE exp._insertDateTimeProperty(@objectid BIGINT, @propid BIGINT, @datetime DATETIME) AS
BEGIN
    IF (@propid IS NULL OR @objectid IS NULL) RETURN
    INSERT INTO exp.ObjectProperty (ObjectId, PropertyId, TypeTag, DateTimeValue)
    VALUES (@objectid, @propid, 'd', @datetime)
END

GO
CREATE OR ALTER PROCEDURE exp._insertStringProperty(@objectid BIGINT, @propid BIGINT, @string VARCHAR(400)) AS
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

CREATE OR ALTER PROCEDURE exp.setFloatProperties(
    @propertyid BIGINT,
    @objectid1 BIGINT, @float1 FLOAT,
    @objectid2 BIGINT, @float2 FLOAT,
    @objectid3 BIGINT, @float3 FLOAT,
    @objectid4 BIGINT, @float4 FLOAT,
    @objectid5 BIGINT, @float5 FLOAT,
    @objectid6 BIGINT, @float6 FLOAT,
    @objectid7 BIGINT, @float7 FLOAT,
    @objectid8 BIGINT, @float8 FLOAT,
    @objectid9 BIGINT, @float9 FLOAT,
    @objectid10 BIGINT, @float10 FLOAT
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
CREATE OR ALTER PROCEDURE exp.setStringProperties(
    @propertyid BIGINT,
    @objectid1 BIGINT, @string1 VARCHAR(400),
    @objectid2 BIGINT, @string2 VARCHAR(400),
    @objectid3 BIGINT, @string3 VARCHAR(400),
    @objectid4 BIGINT, @string4 VARCHAR(400),
    @objectid5 BIGINT, @string5 VARCHAR(400),
    @objectid6 BIGINT, @string6 VARCHAR(400),
    @objectid7 BIGINT, @string7 VARCHAR(400),
    @objectid8 BIGINT, @string8 VARCHAR(400),
    @objectid9 BIGINT, @string9 VARCHAR(400),
    @objectid10 BIGINT, @string10 VARCHAR(400)
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
CREATE OR ALTER PROCEDURE exp.setDateTimeProperties(
    @propertyid BIGINT,
    @objectid1 BIGINT, @datetime1 DATETIME,
    @objectid2 BIGINT, @datetime2 DATETIME,
    @objectid3 BIGINT, @datetime3 DATETIME,
    @objectid4 BIGINT, @datetime4 DATETIME,
    @objectid5 BIGINT, @datetime5 DATETIME,
    @objectid6 BIGINT, @datetime6 DATETIME,
    @objectid7 BIGINT, @datetime7 DATETIME,
    @objectid8 BIGINT, @datetime8 DATETIME,
    @objectid9 BIGINT, @datetime9 DATETIME,
    @objectid10 BIGINT, @datetime10 DATETIME
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
CREATE OR ALTER PROCEDURE exp.deleteObjectById(@container ENTITYID, @objectid BIGINT) AS
BEGIN
    SET NOCOUNT ON
    SELECT @objectid = ObjectId FROM exp.Object WHERE Container=@container AND ObjectId=@objectid
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
