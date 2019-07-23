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
  RowId INT IDENTITY(1,1) NOT NULL,
  Name NVARCHAR(255) NOT NULL,
  CreatedBy USERID,
  ModifiedBy USERID,
  Created DATETIME,
  Modified DATETIME,

  Enabled BIT NOT NULL DEFAULT 0,
  Type NVARCHAR(64),
  Description NVARCHAR(255),
  Configuration NVARCHAR(MAX),

  CONSTRAINT PK_ReportEngines PRIMARY KEY (RowId),
  CONSTRAINT UQ_Name_Type UNIQUE (Name, Type)
);

EXEC core.executeJavaUpgradeCode 'migrateEngineConfigurations';
