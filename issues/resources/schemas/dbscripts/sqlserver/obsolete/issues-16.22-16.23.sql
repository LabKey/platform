/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

DROP INDEX IX_Issues_AssignedTo ON issues.issues;
DROP INDEX IX_Issues_Status ON issues.issues;

-- Must drop default constraint before dropping column
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Priority'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Created'
EXEC core.fn_dropifexists @objname='Issues', @objschema='issues', @objtype='DEFAULT', @subobjname='Modified'

-- delete obsolete fields from the issues.issues table
EXEC core.executeJavaUpgradeCode 'dropLegacyFields';