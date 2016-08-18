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

CREATE TABLE exp.Alias
(
  RowId INT IDENTITY (1, 1) NOT NULL,
  Created DATETIME,
  CreatedBy INT,
  Modified DATETIME,
  ModifiedBy INT,

  Name NVARCHAR(500) NOT NULL,

  CONSTRAINT PK_Alias PRIMARY KEY (RowId),
  CONSTRAINT UQ_Alias_Name UNIQUE (Name)
);

CREATE TABLE exp.DataAliasMap
(
  LSID LSIDtype NOT NULL,
  Alias INT NOT NULL,
  Container EntityId NOT NULL,

  CONSTRAINT PK_DataAliasMap PRIMARY KEY (LSID, Alias),
  CONSTRAINT FK_DataAlias_RowId FOREIGN KEY (Alias) REFERENCES exp.Alias(RowId)
);

CREATE TABLE exp.MaterialAliasMap
(
  LSID LSIDtype NOT NULL,
  Alias INT NOT NULL,
  Container EntityId NOT NULL,

  CONSTRAINT PK_MaterialAliasMap PRIMARY KEY (LSID, Alias),
  CONSTRAINT FK_MaterialAlias_RowId FOREIGN KEY (Alias) REFERENCES exp.Alias(RowId)
);
