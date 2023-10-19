ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INTEGER;

UPDATE exp.material AS mat SET RootMaterialRowId = (
    SELECT DISTINCT RowId FROM exp.material AS expmat WHERE mat.RootMaterialLSID = expmat.LSID
);

ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;

CREATE INDEX uq_material_rootrowid ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
