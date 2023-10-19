ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INTEGER;
UPDATE exp.material AS M SET RootMaterialRowId = (
    SELECT DISTINCT RowId FROM exp.material AS ExpMatFrom WHERE M.RootMaterialLSID = ExpMatFrom.LSID
);
ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;
CREATE INDEX IF NOT EXISTS uq_material_rootrowid ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
