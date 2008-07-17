/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
ALTER TABLE core.ACLs ADD Container UNIQUEIDENTIFIER;
GO
UPDATE core.ACLs SET Container = ObjectId WHERE ObjectId IN (SELECT EntityId FROM core.Containers);
GO

ALTER TABLE core.Principals ADD Type CHAR(1) NOT NULL DEFAULT 'u';
GO
UPDATE core.Principals SET Type='u';
UPDATE core.Principals SET Type='g' WHERE IsGroup = '1';
GO

ALTER TABLE core.Principals ADD Container ENTITYID NULL;
ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_ProjectId_Name;
GO
UPDATE core.Principals SET Container = ProjectId;
GO
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name UNIQUE (Container, Name);
GO

DECLARE @name VARCHAR(200)
DECLARE @sql VARCHAR(4000)
SELECT @name = name FROM sysobjects WHERE name LIKE 'DF__Principal__IsGro%'
IF (@name IS NOT NULL)
BEGIN
    SELECT @sql = 'ALTER TABLE core.Principals DROP CONSTRAINT ' + @name
    EXEC sp_sqlexec @sql
END
GO

IF object_id('core.DF_Principals_IsGroup','d') IS NOT NULL
    ALTER TABLE core.Principals DROP CONSTRAINT DF_Principals_IsGroup
GO

ALTER TABLE core.Principals DROP COLUMN IsGroup;
GO

ALTER TABLE core.Principals ADD OwnerId ENTITYID NULL;
GO
UPDATE core.Principals SET OwnerId = Container;
GO

ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name;
GO
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId);
GO
ALTER TABLE core.Principals DROP COLUMN ProjectId;
GO

