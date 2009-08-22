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
UPDATE core.ACLs SET Container = ObjectId WHERE ObjectId IN (SELECT EntityId FROM core.Containers);

ALTER TABLE core.Principals ADD Type CHAR(1) NOT NULL DEFAULT 'u';
UPDATE core.Principals SET Type='u';
UPDATE core.Principals SET Type='g' WHERE IsGroup = '1';

ALTER TABLE core.Principals ADD Container ENTITYID NULL;
ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_ProjectId_Name;
UPDATE core.Principals SET Container = ProjectId;
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name UNIQUE (Container, Name);

ALTER TABLE core.Principals DROP IsGroup;

ALTER TABLE core.Principals ADD OwnerId ENTITYID NULL;
UPDATE core.Principals SET OwnerId = Container;

ALTER TABLE core.Principals DROP CONSTRAINT UQ_Principals_Container_Name;
ALTER TABLE core.Principals ADD CONSTRAINT UQ_Principals_Container_Name_OwnerId UNIQUE (Container, Name, OwnerId);
ALTER TABLE core.Principals DROP COLUMN ProjectId;