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
CREATE TABLE core.ReportEngines
(
  RowId SERIAL,
  Name VARCHAR(255) NOT NULL,
  CreatedBy USERID,
  ModifiedBy USERID,
  Created TIMESTAMP,
  Modified TIMESTAMP,

  Enabled BOOLEAN NOT NULL DEFAULT FALSE,
  Type VARCHAR(64) NOT NULL,
  Description VARCHAR(255),
  Configuration TEXT,

  CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
  CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

SELECT core.executeJavaUpgradeCode('migrateEngineConfigurations');

