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
/* issues-16.10-16.11.sql */

CREATE SCHEMA IssueDef;
GO

CREATE TABLE issues.IssueDef
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Name NVARCHAR(200) NOT NULL,

  Container ENTITYID NOT NULL,
  Created DATETIME,
  Modified DATETIME,
  CreatedBy INTEGER,
  ModifiedBy INTEGER,

  CONSTRAINT PK_IssueDef PRIMARY KEY (RowId),
  CONSTRAINT UQ_IssueDef_Container_Name UNIQUE (Name, Container)
);

/* issues-16.11-16.12.sql */

-- Rename from IssueDef to IssueListDef
EXEC sp_rename 'issues.IssueDef', 'IssueListDef'
GO

ALTER TABLE issues.Issues ADD IssueDefId INTEGER;
ALTER TABLE issues.Issues ADD CONSTRAINT FK_IssueListDef_IssueDefId_RowId FOREIGN KEY (IssueDefId) REFERENCES issues.IssueListDef(RowId);

/* issues-16.12-16.13.sql */

ALTER TABLE issues.IssueListDef ADD Label NVARCHAR(200);
GO
UPDATE issues.IssueListDef SET Label = Name;
ALTER TABLE issues.IssueListDef ALTER COLUMN Label NVARCHAR(200) NOT NULL;

/* issues-16.13-16.14.sql */

EXEC core.executeJavaUpgradeCode 'upgradeIssuesTables';