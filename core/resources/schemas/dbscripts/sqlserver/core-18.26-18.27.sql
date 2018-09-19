
UPDATE core.ReportEngines SET Type = 'External' WHERE TYPE IS NULL;
ALTER TABLE core.ReportEngines DROP CONSTRAINT UQ_Name_Type;
ALTER TABLE core.ReportEngines ALTER COLUMN Type NVARCHAR(64) NOT NULL;
ALTER TABLE core.ReportEngines ADD CONSTRAINT UQ_Name_Type UNIQUE (Name, Type);
