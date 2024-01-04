-- Add new "RootMaterialRowId" column
ALTER TABLE exp.Material ADD rootmaterialrowid INTEGER NULL;
GO

-- Update all exp.Material rows to have a "RootMaterialRowId"
UPDATE Material
SET Material.rootmaterialrowid = Parent.rowid
FROM exp.Material Material
INNER JOIN exp.Material Parent ON Material.rootmateriallsid = Parent.lsid;
GO

-- Issue 49328: Ensure RootMaterialRowId is set for all materials
UPDATE exp.Material SET rootmaterialrowid = rowid WHERE rootmaterialrowid IS NULL;
GO

-- Add NOT NULL constraint to "RootMaterialRowId"
ALTER TABLE exp.Material ALTER COLUMN rootmaterialrowid INTEGER NOT NULL;
GO

-- Add FK on "RootMaterialRowId"
-- See exp-23.012-23.013.sql
-- ALTER TABLE exp.Material ADD CONSTRAINT FK_Material_RootMaterialRowId
--     FOREIGN KEY (RootMaterialRowId) REFERENCES exp.Material (RowId);
-- GO

CREATE INDEX ix_material_rootmaterialrowid ON exp.Material (rootmaterialrowid);
GO

-- Remove the "RootMaterialLSID" column
EXEC core.fn_dropifexists 'material', 'exp', 'INDEX', 'uq_material_rootlsid';
EXEC core.fn_dropifexists 'material', 'exp', 'COLUMN', 'rootmateriallsid';

EXEC core.executeJavaUpgradeCode 'addRowIdToMaterializedSampleTypes';
