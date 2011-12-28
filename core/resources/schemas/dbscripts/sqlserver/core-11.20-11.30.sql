/*
 * Copyright (c) 2011 LabKey Corporation
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
/* core-11.20-11.21.sql */

DROP TABLE core.UserHistory;

/* core-11.21-11.22.sql */

-- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
ALTER TABLE core.Modules
    ADD AutoUninstall BIT NOT NULL DEFAULT 0;

-- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing
ALTER TABLE core.Modules
    ADD Schemas NVARCHAR(100) NULL;

GO

UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'workbook' WHERE ClassName = 'org.labkey.workbook.WorkbookModule';
UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'cabig' WHERE ClassName = 'org.labkey.cabig.caBIGModule';

/* core-11.22-11.23.sql */

-- represents a grouping category for views (reports etc.)
CREATE TABLE core.ViewCategory
(
    RowId INT IDENTITY(1,1) NOT NULL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created DATETIME DEFAULT getdate(),
    ModifiedBy USERID,
    Modified DATETIME DEFAULT getdate(),

    Label NVARCHAR(200) NOT NULL,
    DisplayOrder INT NOT NULL DEFAULT 0,

    CONSTRAINT pk_viewCategory PRIMARY KEY (RowId),
    CONSTRAINT uq_container_label UNIQUE (Container, Label)
);

ALTER TABLE core.Report ADD CategoryId INT;
ALTER TABLE core.Report ADD DisplayOrder INT NOT NULL DEFAULT 0;

/* core-11.25-11.26.sql */

-- change Containers.Name to nvarchar
EXEC core.fn_dropifexists 'Containers', 'core', 'CONSTRAINT', 'UQ_Containers_Parent_Name';

ALTER TABLE core.Containers
    ALTER COLUMN Name nvarchar(255) NULL;

ALTER TABLE core.Containers
    ADD CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name);


-- change ContainerAliases.Path to nvarchar
EXEC core.fn_dropifexists 'ContainerAliases', 'core', 'CONSTRAINT', 'UK_ContainerAliases_Paths';

ALTER TABLE core.ContainerAliases
    ALTER COLUMN Path nvarchar(255) NULL;

ALTER TABLE core.ContainerAliases
    ADD CONSTRAINT UQ_ContainerAliases_Paths UNIQUE (Path);

-- change MappedDirectories.Name and .Path to nvarchar
EXEC core.fn_dropifexists 'MappedDirectories', 'core', 'CONSTRAINT', 'UQ_MappedDirectories';

ALTER TABLE core.MappedDirectories
    ALTER COLUMN Name NVARCHAR(80) NULL;

ALTER TABLE core.MappedDirectories
    ALTER COLUMN Path NVARCHAR(255) NULL;

ALTER TABLE core.MappedDirectories
    ADD CONSTRAINT UQ_MappedDirectories UNIQUE (Container, Name);
