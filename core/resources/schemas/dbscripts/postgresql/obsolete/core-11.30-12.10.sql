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

/* core-11.31-11.32.sql */

DELETE FROM core.MvIndicators WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

/* core-11.32-11.33.sql */

-- Make sure that guests and general site users don't have access to root container
DELETE FROM core.RoleAssignments WHERE
  ResourceId IN (SELECT EntityId FROM core.Containers WHERE Parent IS NULL)
  AND UserId IN(-2, -3);