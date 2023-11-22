-- It is possible for parent samples of aliquots to be moved to a subfolder where upon deletion
-- of the subfolder we need to delete the parent sample but the aliquot still exists.
ALTER TABLE exp.material DROP CONSTRAINT IF EXISTS FK_Material_RootMaterialRowId;
