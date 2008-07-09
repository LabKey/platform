/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

-- Start over to reconfigure Containers handling
EXEC core.fn_dropifexists '*', 'cabig', 'SCHEMA'
go


EXEC sp_addapprole 'cabig', 'password'
go


-- If caBIG is enabled, all containers with caBIG bit set.  Self join to Containers to get ParentId (used directly
-- by caBIG interface to navigate the folder hierarchy).  LEFT OUTER JOIN ensures that root container can be published;
-- Constraint on Containers table ensures that all Containers (except root) have a parent.
