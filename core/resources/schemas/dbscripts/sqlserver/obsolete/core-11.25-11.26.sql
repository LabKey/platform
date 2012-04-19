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

-- change Containers.Name to nvarchar
EXEC core.fn_dropifexists 'Containers', 'core', 'CONSTRAINT', 'UQ_Containers_Parent_Name'

ALTER TABLE core.Containers
    ALTER COLUMN Name nvarchar(255) NULL

ALTER TABLE core.Containers
    ADD CONSTRAINT UQ_Containers_Parent_Name UNIQUE (Parent, Name)


-- change ContainerAliases.Path to nvarchar
EXEC core.fn_dropifexists 'ContainerAliases', 'core', 'CONSTRAINT', 'UK_ContainerAliases_Paths'

ALTER TABLE core.ContainerAliases
    ALTER COLUMN Path nvarchar(255) NULL

ALTER TABLE core.ContainerAliases
    ADD CONSTRAINT UQ_ContainerAliases_Paths UNIQUE (Path)


-- change MappedDirectories.Name and .Path to nvarchar
EXEC core.fn_dropifexists 'MappedDirectories', 'core', 'CONSTRAINT', 'UQ_MappedDirectories'

ALTER TABLE core.MappedDirectories
    ALTER COLUMN Name nvarchar(80) NULL

ALTER TABLE core.MappedDirectories
    ALTER COLUMN Path nvarchar(255) NULL

ALTER TABLE core.MappedDirectories
    ADD CONSTRAINT UQ_MappedDirectories UNIQUE (Container, Name)
