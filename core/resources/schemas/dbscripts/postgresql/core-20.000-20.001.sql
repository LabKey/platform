ALTER TABLE core.Modules RENAME COLUMN InstalledVersion TO SchemaVersion;
ALTER TABLE core.Modules ALTER COLUMN SchemaVersion DROP NOT NULL;