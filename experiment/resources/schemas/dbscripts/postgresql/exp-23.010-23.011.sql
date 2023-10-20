ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INTEGER;

UPDATE exp.material AS mat SET RootMaterialRowId = (
    -- This DISTINCT is logically superfluous but it does seem to help Postgresql choose a better query plan
    SELECT DISTINCT RowId FROM exp.material AS expmat WHERE mat.RootMaterialLSID = expmat.LSID
);

ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;

CREATE INDEX ix_material_rootrowid ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
