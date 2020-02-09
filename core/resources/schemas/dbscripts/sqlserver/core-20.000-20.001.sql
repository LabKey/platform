sp_rename 'core.Modules.InstalledVersion', 'SchemaVersion', 'COLUMN';
ALTER TABLE core.Modules ALTER COLUMN SchemaVersion FLOAT NULL;