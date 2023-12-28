-- Create an unconstrained temporary table to write results
CREATE TEMPORARY TABLE materialroottemp (RowId INT, RootMaterialRowId INT);

-- Compute "RootMaterialRowId"
INSERT INTO materialroottemp (RowId, RootMaterialRowId)
SELECT m.RowId, x.RowId AS RootMaterialRowId
FROM exp.material AS m LEFT JOIN exp.material AS x ON m.RootMaterialLSID = x.LSID;

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
DROP INDEX IF EXISTS exp.uq_material_rootlsid;

-- Add new "RootMaterialRowId" column
ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INT;

-- Add PK constraint to temporary table to assist JOIN against RowId
ALTER TABLE materialroottemp ADD CONSTRAINT PK_materialroottemp PRIMARY KEY (RowId);

-- Update all exp.material rows to have a "RootMaterialRowId"
UPDATE exp.material AS mat SET RootMaterialRowId = (
    SELECT RootMaterialRowId FROM materialroottemp AS root WHERE mat.RowId = root.RowId
);

-- Issue 49328: Ensure RootMaterialRowId is set for all materials
UPDATE exp.material as mat SET RootMaterialRowId = RowId WHERE RootMaterialRowId IS NULL;

-- Drop the temporary table
DROP TABLE materialroottemp;

-- Add NOT NULL constraint to "RootMaterialRowId"
ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;

-- Add FK on "RootMaterialRowId"
-- See exp-23.012-23.013.sql
-- ALTER TABLE exp.material ADD CONSTRAINT FK_Material_RootMaterialRowId
--     FOREIGN KEY (RootMaterialRowId) REFERENCES exp.material (RowId);

-- Remove the "RootMaterialLSID" column
ALTER TABLE exp.material DROP COLUMN RootMaterialLSID;

-- Recreate indices on exp.material
CREATE INDEX IDX_CL_Material_RunId ON exp.material (RunId);
CREATE INDEX IX_Material_Container ON exp.material (Container);
CREATE INDEX IX_Material_SourceApplicationId ON exp.material (SourceApplicationId);
CREATE INDEX IX_Material_CpasType ON exp.material (CpasType);
CREATE UNIQUE INDEX idx_material_AK ON exp.material (container, cpastype, name) WHERE cpastype IS NOT NULL;
CREATE UNIQUE INDEX idx_material_objectid ON exp.material (objectid);
CREATE INDEX IDX_material_name_sourceid ON exp.material (name, materialSourceId);
CREATE INDEX IX_Material_RootMaterialRowId ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
