ALTER TABLE core.Modules
    ADD COLUMN AutoUninstall BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
    ADD COLUMN Schemas VARCHAR(100) NULL;                     -- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing

UPDATE core.Modules SET AutoUninstall = TRUE, Schemas = 'workbook' WHERE ClassName = 'org.labkey.workbook.WorkbookModule';
UPDATE core.Modules SET AutoUninstall = TRUE, Schemas = 'cabig' WHERE ClassName = 'org.labkey.cabig.caBIGModule';

-- Lowercase version; PostgreSQL only
UPDATE core.Modules SET AutoUninstall = TRUE WHERE Name = 'illumina' AND ClassName = 'org.labkey.api.module.SimpleModule';
