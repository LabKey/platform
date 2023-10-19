ALTER TABLE exp.material ADD COLUMN RootMaterialRowId INTEGER;

-- TODO: Could consider this UPDATE, however, the performance seems to be much worse...
-- UPDATE exp.material
--     SET RootMaterialRowId = Parent.RowId
--     FROM exp.material AS Parent
--     WHERE Material.RootMaterialLSID = Parent.LSID;
UPDATE exp.material AS mat SET RootMaterialRowId = (
    -- This DISTINCT here is logically superfluous but it does seem to help Postgresql choose a better query plan
    SELECT DISTINCT RowId FROM exp.material AS expmat WHERE mat.RootMaterialLSID = expmat.LSID
);

ALTER TABLE exp.material ALTER COLUMN RootMaterialRowId SET NOT NULL;

CREATE INDEX ix_material_rootrowid ON exp.material (RootMaterialRowId);

SELECT core.executeJavaUpgradeCode('addRowIdToMaterializedSampleTypes');
