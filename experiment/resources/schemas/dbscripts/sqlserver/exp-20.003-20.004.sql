EXEC core.executeJavaUpgradeCode 'addDbSequenceForMaterialsRowId';

ALTER TABLE exp.material ADD RowId_copy INT NULL;

UPDATE exp.material SET RowId_copy = RowId;

ALTER TABLE exp.material DROP COLUMN RowId;

EXEC sp_rename 'exp.material.RowId_copy', 'RowId', 'COLUMN';