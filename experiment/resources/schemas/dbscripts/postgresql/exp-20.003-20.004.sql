SELECT core.executeJavaUpgradeCode('addDbSequenceForMaterialsRowId');

ALTER SEQUENCE exp.material_rowid_seq owned by NONE;
ALTER TABLE exp.material ALTER COLUMN rowId DROP DEFAULT;
DROP SEQUENCE exp.material_rowid_seq;