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

/* prop-12.11-12.12.sql */

-- Clean out any Properties that belong to a PropertySet that doesn't have a Container (ObjectId) anymore
DELETE FROM prop.properties WHERE "set" IN
  (SELECT "set" FROM prop.propertysets WHERE ObjectId NOT IN
    (SELECT EntityId FROM core.Containers));

-- Clean out any Properties that belong to a PropertySet that doesn't exist anymore
DELETE FROM prop.properties WHERE "set" NOT IN
  (SELECT "set" FROM prop.propertysets);

-- Clean out PropertySets that don't have a Container (ObjectId) anymore
DELETE FROM prop.propertysets WHERE ObjectId NOT IN
  (SELECT EntityId FROM core.Containers);

-- Create real FKs to prevent orphaning in the future
ALTER TABLE prop.properties
    ADD CONSTRAINT FK_Properties_Set FOREIGN KEY ("set") REFERENCES prop.PropertySets ("set");

ALTER TABLE prop.propertysets
    ADD CONSTRAINT FK_PropertySets_ObjectId FOREIGN KEY (ObjectId) REFERENCES core.Containers (EntityId);