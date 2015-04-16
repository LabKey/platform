/*
 * Copyright (c) 2014-2015 LabKey Corporation
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

-- delete any orphaned reports
DELETE FROM core.Report WHERE ContainerId NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE core.Report
ADD CONSTRAINT FK_Report_ContainerId FOREIGN KEY (ContainerId) REFERENCES core.Containers (EntityId);
