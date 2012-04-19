/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
-- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
ALTER TABLE core.Modules
    ADD AutoUninstall BIT NOT NULL DEFAULT 0;

-- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing
ALTER TABLE core.Modules
    ADD Schemas NVARCHAR(100) NULL;

GO

UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'workbook' WHERE ClassName = 'org.labkey.workbook.WorkbookModule';
UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'cabig' WHERE ClassName = 'org.labkey.cabig.caBIGModule';
