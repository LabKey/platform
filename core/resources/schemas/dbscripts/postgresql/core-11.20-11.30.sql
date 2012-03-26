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

ALTER TABLE core.Modules
    ADD COLUMN AutoUninstall BOOLEAN NOT NULL DEFAULT FALSE,  -- TRUE means LabKey should uninstall this module (drop schemas, delete SqlScripts rows, delete Modules rows), if it no longer exists
    ADD COLUMN Schemas VARCHAR(100) NULL;                     -- Schemas managed by this module; LabKey will drop these schemas when a module marked AutoUninstall = TRUE is missing

UPDATE core.Modules SET AutoUninstall = TRUE, Schemas = 'workbook' WHERE ClassName = 'org.labkey.workbook.WorkbookModule';
UPDATE core.Modules SET AutoUninstall = TRUE, Schemas = 'cabig' WHERE ClassName = 'org.labkey.cabig.caBIGModule';

-- Lowercase version; PostgreSQL only
UPDATE core.Modules SET AutoUninstall = TRUE WHERE Name = 'illumina' AND ClassName = 'org.labkey.api.module.SimpleModule';

SELECT core.executeJavaUpgradeCode('handleUnknownModules');

/* core-11.22-11.23.sql */

-- represents a grouping category for views (reports etc.)
CREATE TABLE core.ViewCategory
(
    RowId SERIAL,
    Container ENTITYID NOT NULL,
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    ModifiedBy USERID,
    Modified TIMESTAMP DEFAULT now(),

    Label VARCHAR(200) NOT NULL,
    DisplayOrder Integer NOT NULL DEFAULT 0,

    CONSTRAINT pk_viewCategory PRIMARY KEY (RowId),
    CONSTRAINT uq_container_label UNIQUE (Container, Label)
);

ALTER TABLE core.Report ADD COLUMN CategoryId Integer;
ALTER TABLE core.Report ADD COLUMN DisplayOrder Integer NOT NULL DEFAULT 0;

/* core-11.24-11.25.sql */

CREATE AGGREGATE core.array_accum(text) (
    SFUNC = array_append,
    STYPE = text[],
    INITCOND = '{}',
    SORTOP = >
);
