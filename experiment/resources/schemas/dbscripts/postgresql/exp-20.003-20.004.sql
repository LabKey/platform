SELECT core.executeJavaUpgradeCode('addDbSequenceForMaterialsRowId');

-- TODO need to remove foreign key constraints and add them back
ALTER SEQUENCE material_rowid_seq owned by NONE;
ALTER TABLE material ALTER COLUMN rowId DROP DEFAULT;
DROP SEQUENCE material_row_id;