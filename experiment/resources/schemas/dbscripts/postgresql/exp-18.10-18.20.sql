/*
 * Copyright (c) 2018 LabKey Corporation
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
/* exp-18.10-18.11.sql */

SELECT core.executeJavaUpgradeCode('rebuildAllEdges');

/* exp-18.11-18.12.sql */

ALTER TABLE exp.propertydescriptor
  ADD TextExpression varchar(200) NULL;

/* exp-18.12-18.13.sql */

CREATE TABLE exp.Exclusions
(
  RowId SERIAL NOT NULL,
  RunId INT NOT NULL,
  Comment TEXT NULL,
  Created TIMESTAMP NULL,
  CreatedBy INT NULL,
  Modified TIMESTAMP NULL,
  ModifiedBy INT NULL,
  CONSTRAINT PK_Exclusion_RowId PRIMARY KEY (RowId),
  CONSTRAINT FK_Exclusion_RunId FOREIGN KEY (RunId) REFERENCES exp.ExperimentRun (RowId)
);
CREATE INDEX IX_Exclusion_RunId ON exp.Exclusions(RunId);

CREATE TABLE exp.ExclusionMaps
(
  RowId SERIAL NOT NULL,
  ExclusionId INT NOT NULL,
  DataRowId INT NOT NULL,
  Created TIMESTAMP NULL,
  CreatedBy INT NULL,
  Modified TIMESTAMP NULL,
  ModifiedBy INT NULL,
  CONSTRAINT PK_ExclusionMap_RowId PRIMARY KEY (RowId),
  CONSTRAINT FK_ExclusionMap_ExclusionId FOREIGN KEY (ExclusionId) REFERENCES exp.Exclusions (RowId),
  CONSTRAINT UQ_ExclusionMap_ExclusionId_DataId UNIQUE (ExclusionId, DataRowId)
);

/* exp-18.13-18.14.sql */

ALTER TABLE exp.DomainDescriptor ALTER COLUMN DomainURI TYPE VARCHAR(300);
ALTER TABLE exp.PropertyDescriptor ALTER COLUMN PropertyURI TYPE VARCHAR(300);