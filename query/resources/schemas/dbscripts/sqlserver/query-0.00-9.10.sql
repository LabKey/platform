/*
 * Copyright (c) 2011 LabKey Corporation
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

/* query-0.00-8.30.sql */

EXEC sp_addapprole 'query', 'password';

CREATE TABLE query.QueryDef
(
    QueryDefId INT IDENTITY(1, 1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container uniqueidentifier NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    Sql NTEXT,
    MetaData NTEXT,
    Description NTEXT,
    SchemaVersion FLOAT NOT NULL,
    Flags INT NOT NULL,
    CONSTRAINT PK_QueryDef PRIMARY KEY (QueryDefId),
    CONSTRAINT UQ_QueryDef UNIQUE (Container, "Schema", Name)
)
GO

CREATE TABLE query.CustomView
(
    CustomViewId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    QueryName NVARCHAR(50) NOT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(50) NULL,
    CustomViewOwner INT NULL,
    Columns NTEXT,
    Filter NTEXT,
    Flags INT NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, "Schema", QueryName, CustomViewOwner, Name)
)
GO

CREATE TABLE query.DbUserSchema
(
    DbUserSchemaId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy int NULL,
    Modified DATETIME NULL,
    ModifiedBy int NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    UserSchemaName NVARCHAR(50) NOT NULL,
    DbSchemaName NVARCHAR(50) NULL,
    DbContainer UNIQUEIDENTIFIER NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container,UserSchemaName)
)
GO

ALTER TABLE query.dbuserschema ADD
    editable BIT DEFAULT 0,
    metadata NTEXT NULL
GO

ALTER TABLE query.customview ALTER COLUMN queryname nvarchar(200)
GO

ALTER TABLE query.dbuserschema ALTER COLUMN dbschemaname NVARCHAR(50) NOT NULL
GO

CREATE TABLE query.QuerySnapshotDef
(
    RowId INT IDENTITY(1,1) NOT NULL,
    QueryDefId INT NULL,

    EntityId ENTITYID NOT NULL,
    Created DATETIME NULL,
    CreatedBy int NULL,
    Modified DATETIME NULL,
    ModifiedBy int NULL,
    Container ENTITYID NOT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    Name NVARCHAR(50) NOT NULL,
    Columns TEXT,
    Filter TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);
GO

ALTER TABLE query.QueryDef ALTER COLUMN Name NVARCHAR(200) NOT NULL
GO
ALTER TABLE query.QuerySnapshotDef ALTER COLUMN Name NVARCHAR(200) NOT NULL
GO
ALTER TABLE query.QuerySnapshotDef ADD LastUpdated DATETIME NULL
GO
ALTER TABLE query.QuerySnapshotDef ADD NextUpdate DATETIME NULL
GO
ALTER TABLE query.QuerySnapshotDef ADD UpdateDelay INT DEFAULT 0
GO
ALTER TABLE query.QuerySnapshotDef ADD ViewName NVARCHAR(50) NULL
GO
ALTER TABLE query.QuerySnapshotDef ADD QueryTableName NVARCHAR(200) NULL
GO
ALTER TABLE query.QuerySnapshotDef DROP COLUMN ViewName
GO

/* query-8.30-9.10.sql */

ALTER TABLE query.customview ALTER COLUMN name nvarchar(200)
GO