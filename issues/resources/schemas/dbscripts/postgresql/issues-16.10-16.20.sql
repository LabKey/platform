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

CREATE TABLE issues.IssueDef
(
    RowId SERIAL NOT NULL,
    Name VARCHAR(200) NOT NULL,

    Container ENTITYID NOT NULL,
    Created TIMESTAMP,
    Modified TIMESTAMP,
    CreatedBy INTEGER,
    ModifiedBy INTEGER,

    CONSTRAINT PK_IssueDef PRIMARY KEY (RowId),
    CONSTRAINT UQ_IssueDef_Container_Name UNIQUE (Name, Container)
);

/* issues-16.11-16.12.sql */

-- Rename from IssueDef to IssueListDef
ALTER TABLE issues.IssueDef RENAME TO IssueListDef;


ALTER TABLE issues.Issues ADD COLUMN IssueDefId INTEGER;
ALTER TABLE issues.Issues ADD CONSTRAINT FK_IssueListDef_IssueDefId_RowId FOREIGN KEY (IssueDefId) REFERENCES issues.IssueListDef(RowId);

/* issues-16.12-16.13.sql */

ALTER TABLE issues.IssueListDef ADD COLUMN Label VARCHAR(200);
UPDATE issues.IssueListDef SET Label = Name;
ALTER TABLE issues.IssueListDef ALTER COLUMN Label SET NOT NULL;

/* issues-16.13-16.14.sql */

SELECT core.executeJavaUpgradeCode('upgradeIssuesTables');