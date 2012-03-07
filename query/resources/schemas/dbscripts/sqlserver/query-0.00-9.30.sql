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

CREATE SCHEMA query;
GO

CREATE TABLE query.QueryDef
(
    QueryDefId INT IDENTITY(1, 1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    Sql NTEXT,
    MetaData NTEXT,
    Description NTEXT,
    SchemaVersion FLOAT NOT NULL,
    Flags INT NOT NULL,
    CONSTRAINT PK_QueryDef PRIMARY KEY (QueryDefId),
    CONSTRAINT UQ_QueryDef UNIQUE (Container, "Schema", Name)
);

CREATE TABLE query.CustomView
(
    CustomViewId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NOT NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    QueryName NVARCHAR(200) NOT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    Name NVARCHAR(200) NULL,
    CustomViewOwner INT NULL,
    Columns NTEXT,
    Filter NTEXT,
    Flags INT NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, "Schema", QueryName, CustomViewOwner, Name)
);

CREATE TABLE query.DbUserSchema
(
    DbUserSchemaId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    UserSchemaName NVARCHAR(50) NOT NULL,
    DbSchemaName NVARCHAR(50) NOT NULL,
    DbContainer UNIQUEIDENTIFIER NULL,
    Editable BIT DEFAULT 0,
    MetaData NTEXT NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container,UserSchemaName)
);

CREATE TABLE query.QuerySnapshotDef
(
    RowId INT IDENTITY(1,1) NOT NULL,
    QueryDefId INT NULL,

    EntityId ENTITYID NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,
    "Schema" NVARCHAR(50) NOT NULL,
    Name NVARCHAR(200) NOT NULL,
    Columns TEXT,
    Filter TEXT,
    LastUpdated DATETIME NULL,
    NextUpdate DATETIME NULL,
    UpdateDelay INT DEFAULT 0,
    QueryTableName NVARCHAR(200) NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

/* query-9.20-9.30.sql */

-- Support other DataSources in external schemas (e.g., SAS, other PostgreSQL servers, etc.)
ALTER TABLE query.DbUserSchema ADD DataSource NVARCHAR(50) NOT NULL;

-- Remove unused column
ALTER TABLE query.DbUserSchema DROP COLUMN DbContainer;