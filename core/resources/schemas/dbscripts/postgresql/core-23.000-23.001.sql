SELECT core.executeJavaUpgradeCode('removeDuplicateModuleEntries');

-- Switch Name from PK to case-insensitive unique constraint
ALTER TABLE core.Modules DROP CONSTRAINT PK_Modules;
ALTER TABLE core.Modules ALTER COLUMN Name SET NOT NULL;
CREATE UNIQUE INDEX UQ_ModuleName ON core.Modules (LOWER(Name));
