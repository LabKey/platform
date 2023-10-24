-- Drop all indices on exp.material
DROP INDEX IF EXISTS exp.IDX_CL_Material_RunId;
DROP INDEX IF EXISTS exp.IX_Material_Container;
DROP INDEX IF EXISTS exp.IX_Material_SourceApplicationId;
DROP INDEX IF EXISTS exp.IX_Material_CpasType;
DROP INDEX IF EXISTS exp.IDX_Material_LSID;
DROP INDEX IF EXISTS exp.idx_material_AK;
DROP INDEX IF EXISTS exp.idx_material_objectid;
DROP INDEX IF EXISTS exp.IDX_material_name_sourceid;
DROP INDEX IF EXISTS exp.IX_Material_RootRowId;

-- Drop all constraints on exp.material EXCEPT UQ_Material_LSID as it is relied upon by other tables
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_ExperimentRun;
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_ProtocolApplication;
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_Containers;
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_Lsid;
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_ObjectId;
ALTER TABLE exp.material DROP CONSTRAINT FK_Material_SampleState;

-- Add new "RootMaterialRowId" column
ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INT;

-- Create an unconstrained temporary table to write results
CREATE TEMPORARY TABLE materialroottemp
(
    RowId INT,
    RootMaterialRowId INT
) ON COMMIT DROP;

-- Compute "RootMaterialRowId"
INSERT INTO materialroottemp (RowId, RootMaterialRowId)
SELECT m.RowId, x.RowId AS RootMaterialRowId
FROM exp.material AS m LEFT JOIN exp.material AS x ON m.RootMaterialLSID = x.LSID;

-- Add PK constraint to temporary table to assist JOIN against RowId
ALTER TABLE materialroottemp ADD CONSTRAINT PK_materialroottemp PRIMARY KEY (RowId);

-- Update all exp.material rows to have a "RootMaterialRowId"
UPDATE exp.material AS mat SET RootMaterialRowId = (
    SELECT RootMaterialRowId FROM materialroottemp AS root WHERE mat.RowId = root.RowId
);

-- Reintroduce constraints on exp.material
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_ExperimentRun FOREIGN KEY(RunId) REFERENCES exp.ExperimentRun (RowId);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_ProtocolApplication FOREIGN KEY (SourceApplicationID) REFERENCES exp.ProtocolApplication (RowId);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_Containers FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_Lsid FOREIGN KEY (lsid) REFERENCES exp.object (objecturi);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_ObjectId FOREIGN KEY (objectid) REFERENCES exp.object (objectid);
ALTER TABLE exp.material ADD CONSTRAINT FK_Material_SampleState FOREIGN KEY (SampleState) REFERENCES core.DataStates (RowId);

-- Recreate indices on exp.material
CREATE INDEX IDX_CL_Material_RunId ON exp.material (RunId);
CREATE INDEX IX_Material_Container ON exp.material (Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.material (SourceApplicationId);
CREATE INDEX IX_Material_CpasType ON exp.material (CpasType);
CREATE UNIQUE INDEX idx_material_AK ON exp.material (container, cpastype, name) WHERE cpastype IS NOT NULL;
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid);
CREATE INDEX IDX_material_name_sourceid ON exp.material (name, materialSourceId);
CREATE INDEX IX_Material_RootRowId ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
