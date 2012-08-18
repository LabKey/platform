/*
 * Copyright (c) 2012 LabKey Corporation
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

