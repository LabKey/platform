/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

/* core-12.10-12.11.sql */

-- Remove caBIG, #15050
EXEC core.executeJavaUpgradeCode 'handleUnknownModules';

-- Stupid SQL Server can't drop a stupid column that has a stupid default defined without some convoluted SQL like:
DECLARE @defname VARCHAR(100), @cmd VARCHAR(1000)
SET @defname = (SELECT name
    FROM sysobjects so JOIN sysconstraints sc ON so.id = sc.constid
    WHERE object_name(so.parent_obj) = 'Containers'
    AND OBJECT_SCHEMA_NAME(so.parent_obj) = 'core'
    AND so.xtype = 'D'
    AND sc.colid = (SELECT colid FROM syscolumns
        WHERE id = object_id('core.Containers') AND
        name = 'CaBIGPublished'));

IF (@defname IS NOT NULL)
BEGIN
    SET @cmd = 'ALTER TABLE core.Containers DROP CONSTRAINT ' + @defname
    EXEC(@cmd)
END;

ALTER TABLE core.Containers DROP COLUMN CaBIGPublished;

/* core-12.12-12.13.sql */

UPDATE core.Modules SET AutoUninstall = 1, Schemas = 'dataspace' WHERE ClassName = 'org.labkey.dataspace.DataspaceModule';

/* core-12.13-12.14.sql */

-- Note: Commented out in 13.3, since new version is being installed

-- Run the script that installs the CLR GROUP_CONCAT aggregrate functions on SQL Server 2008 and above. Run the script
-- via upgrade code to do the version check and skip the install on SQL Server 2005.
-- EXEC core.executeJavaUpgradeCode 'installGroupConcat';