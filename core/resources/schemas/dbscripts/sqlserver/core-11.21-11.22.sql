-- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
ALTER TABLE core.Modules
    ADD AutoUninstall BIT NOT NULL DEFAULT 0;

-- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing
ALTER TABLE core.Modules
    ADD Schemas NVARCHAR(100) NULL;

GO

UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'workbook' WHERE ClassName = 'org.labkey.workbook.WorkbookModule';
UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'cabig' WHERE ClassName = 'org.labkey.cabig.caBIGModule';
