/*
 * Copyright (c) 2019 LabKey Corporation
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

-- Removing active sample types
EXEC core.fn_dropifexists 'ActiveMaterialSource', 'exp', 'TABLE', NULL;
GO

CREATE SCHEMA expsampleset
go
EXEC core.executeJavaUpgradeCode 'materializeSampleSets';
go

ALTER TABLE exp.materialsource
  ALTER COLUMN nameexpression NVARCHAR(500);

ALTER TABLE exp.dataclass
  ALTER COLUMN nameexpression NVARCHAR(500);

EXEC core.executeJavaUpgradeCode 'addSampleSetGenId';
go

UPDATE exp.data SET datafileurl = 'file:///' + substring(datafileurl, 7, 600) WHERE datafileurl LIKE 'file:/_%' AND datafileurl NOT LIKE 'file:///%' AND datafileurl IS NOT NULL;