/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

/* core-14.20-14.21.sql */

ALTER TABLE core.Report ADD ContentModified DATETIME;
GO
UPDATE core.Report SET ContentModified = Modified;
ALTER TABLE core.Report ALTER COLUMN ContentModified DATETIME NOT NULL;

/* core-14.21-14.22.sql */

-- delete any orphaned reports
DELETE FROM core.Report WHERE ContainerId NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE core.Report
ADD CONSTRAINT FK_Report_ContainerId FOREIGN KEY (ContainerId) REFERENCES core.Containers (EntityId);

/* core-14.22-14.23.sql */

CREATE INDEX IDX_Report_ContainerId ON core.Report(ContainerId);