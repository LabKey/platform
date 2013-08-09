/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

-- Delete "workbook" module
DELETE FROM core.Modules WHERE Name = 'Workbook';
DELETE FROM core.SqlScripts WHERE ModuleName = 'Workbook';
EXEC core.fn_dropifexists '*', 'workbook', 'SCHEMA';

ALTER TABLE Portal.PortalWebParts
    ADD Container ENTITYID NULL;
GO

UPDATE Portal.PortalWebParts SET Container = PageId;

ALTER TABLE Portal.PortalWebParts
    ALTER COLUMN Container ENTITYID NOT NULL;
