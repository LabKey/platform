/*
 * Copyright (c) 2016 LabKey Corporation
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
/* issues-16.20-16.21.sql */

ALTER TABLE issues.issuelistdef ADD kind NVARCHAR(200) NOT NULL DEFAULT 'IssueDefinition';

/* issues-16.21-16.22.sql */

EXEC core.executeJavaUpgradeCode 'upgradeSpecialFields';

/* issues-16.22-16.23.sql */

DROP INDEX IX_Issues_AssignedTo ON issues.issues;
DROP INDEX IX_Issues_Status ON issues.issues;

-- Must drop default constraint before dropping column
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Priority'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Created'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Modified'

-- delete obsolete fields from the issues.issues table
EXEC core.executeJavaUpgradeCode 'dropLegacyFields';

/* issues-16.23-16.24.sql */

-- drop redundant properties from the issues provisioned tables
EXEC core.executeJavaUpgradeCode 'dropRedundantProperties';