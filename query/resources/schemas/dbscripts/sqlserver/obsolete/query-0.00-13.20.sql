/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

/* query-0.00-12.10.sql */

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

CREATE TABLE query.ExternalSchema
(
    ExternalSchemaId INT IDENTITY(1,1) NOT NULL,
    EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL,
    CreatedBy INT NULL,
    Modified DATETIME NULL,
    ModifiedBy INT NULL,

    Container UNIQUEIDENTIFIER NOT NULL,
    DataSource NVARCHAR(50) NOT NULL,
    UserSchemaName NVARCHAR(50) NOT NULL,
    DbSchemaName NVARCHAR(50) NOT NULL,
    Editable BIT NOT NULL DEFAULT 0,
    MetaData NTEXT NULL,
    Indexable BIT NOT NULL DEFAULT 1,
    Tables VARCHAR(8000) NOT NULL DEFAULT '*',  -- Comma-separated list of tables to expose; '*' represents all tables

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(ExternalSchemaId),
    CONSTRAINT UQ_ExternalSchema UNIQUE(Container,UserSchemaName)
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
    QueryTableContainer ENTITYID,
    ParticipantGroups TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

/* query-12.20-12.30.sql */

ALTER TABLE query.querydef ALTER COLUMN "schema" NVARCHAR(200);

/* query-12.301-13.10.sql */

ALTER TABLE query.ExternalSchema
    ADD SchemaType NVARCHAR(50) NOT NULL CONSTRAINT DF_ExternalSchema_SchemaType DEFAULT 'external';

ALTER TABLE query.ExternalSchema
    ADD SchemaTemplate NVARCHAR(50);

EXECUTE sp_rename N'query.ExternalSchema.DbSchemaName', N'SourceSchemaName', 'COLUMN';

ALTER TABLE query.ExternalSchema
    ALTER COLUMN SourceSchemaName NVARCHAR(50) NULL;

GO

-- SchemaTemplate is not compatible with the SourceSchemaName, Tables, and Metadata columns
-- SQLServer doesn't allow CHECK constraints on NTEXT columns so we can't check that MetaData IS NULL when SchemaTemplate IS NOT NULL.
ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK ((SchemaTemplate IS NULL     AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL) OR
           (SchemaTemplate IS NOT NULL AND SourceSchemaName IS NULL     AND Tables IS NULL    ))

-- Remove default '*' and allow null Tables column
EXEC core.fn_dropifexists 'ExternalSchema', 'query', 'DEFAULT', 'Tables';
ALTER TABLE query.ExternalSchema ALTER COLUMN Tables varchar(8000) NULL;

-- Also checked in as query-12.30-12.301 since it's safe to rerun.
-- BE SURE TO CONSOLIDATE QUERY MODULE SCRIPTS STARTING WITH 12.301 for the 13.1 release.

ALTER TABLE query.customview ALTER COLUMN "schema" NVARCHAR(200);

-- Remove check constraint to allow overriding the schema template,
-- but continue to require NOT NULL SourceSchemaName and Tables when SchemaTemplate IS NULL.
ALTER TABLE query.ExternalSchema
   DROP CONSTRAINT "CK_SchemaTemplate";

GO

ALTER TABLE query.ExternalSchema
    ADD CONSTRAINT "CK_SchemaTemplate"
    CHECK (SchemaTemplate IS NOT NULL OR (SchemaTemplate IS NULL AND SourceSchemaName IS NOT NULL AND Tables IS NOT NULL));

/* query-13.10-13.20.sql */

ALTER TABLE query.QuerySnapshotDef ADD OptionsId INT NULL;