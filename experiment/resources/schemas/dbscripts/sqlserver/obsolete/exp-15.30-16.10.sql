/*
 * Copyright (c) 2015-2016 LabKey Corporation
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

/* exp-15.30-15.31.sql */

-- Provisioned schema used by DataClassDomainKind
CREATE SCHEMA expdataclass;
GO

CREATE TABLE exp.DataClass
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Name NVARCHAR(200) NOT NULL,
  LSID LSIDtype NOT NULL,
  Container EntityId NOT NULL,
  Created DATETIME NULL,
  CreatedBy INT NULL,
  Modified DATETIME NULL,
  ModifiedBy INT NULL,
  Description NTEXT NULL,
  MaterialSourceId INT NULL,
  NameExpression NVARCHAR(200) NULL,

  CONSTRAINT PK_DataClass PRIMARY KEY (RowId),
  CONSTRAINT UQ_DataClass_LSID UNIQUE (LSID),
  CONSTRAINT UQ_DataClass_Container_Name UNIQUE (Container, Name),

  CONSTRAINT FK_DataClass_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
  CONSTRAINT FK_DataClass_MaterialSource FOREIGN KEY (MaterialSourceId) REFERENCES exp.MaterialSource (RowId)
);
CREATE INDEX IX_DataClass_Container ON exp.DataClass(Container);


ALTER TABLE exp.data
  ADD description NVARCHAR(4000);

GO

ALTER TABLE exp.data
  ADD classId INT;

GO

ALTER TABLE exp.data
  ADD CONSTRAINT FK_Data_DataClass FOREIGN KEY (classId) REFERENCES exp.DataClass (rowid);

-- Within a DataClass, name must be unique.  If DataClass is null, duplicate names are allowed.
CREATE UNIQUE INDEX UQ_Data_DataClass_Name
  ON exp.data(classId, name) WHERE classId IS NOT NULL;

/* exp-15.31-15.32.sql */

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

/* exp-15.32-15.33.sql */

CREATE INDEX IX_Alias_Name ON exp.Alias(Name);

ALTER TABLE exp.DataAliasMap ADD CONSTRAINT FK_DataAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Data(LSID);
CREATE INDEX IX_DataAliasMap ON exp.DataAliasMap(LSID, Alias, Container);

ALTER TABLE exp.MaterialAliasMap ADD CONSTRAINT FK_MaterialAlias_LSID FOREIGN KEY (LSID) REFERENCES exp.Material(LSID);
CREATE INDEX IX_MaterialAliasMap ON exp.MaterialAliasMap(LSID, Alias, Container);