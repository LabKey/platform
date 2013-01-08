/*
 * Copyright (c) 2012-2013 LabKey Corporation
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


INSERT INTO core.policies
SELECT
   (SELECT entityId FROM core.containers WHERE name IS NULL and parent IS NULL) as resourceid,
   'org.labkey.api.data.Container' as resourceclass,
   (SELECT entityId FROM core.containers where name IS NULL and parent IS NULL) as container
WHERE
   NOT EXISTS (SELECT * from core.policies where resourceid = (SELECT entityId FROM core.containers where name IS NULL and parent IS NULL))
AND
   EXISTS(SELECT entityId FROM core.containers where name IS NULL and parent IS NULL)
;


INSERT INTO core.roleassignments
SELECT
   (SELECT entityId FROM core.containers WHERE  name IS NULL and parent IS NULL) as resourceid,
   -2 as userid,
   'org.labkey.api.security.roles.SeeEmailAddressesRole' as role
WHERE
   NOT EXISTS (SELECT * FROM core.roleassignments WHERE role = 'org.labkey.api.security.roles.SeeEmailAddressesRole')
AND
   EXISTS(SELECT entityId FROM core.containers where name IS NULL and parent IS NULL)
;
