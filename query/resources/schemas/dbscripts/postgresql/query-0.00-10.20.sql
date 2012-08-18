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

CREATE TABLE query.QueryDef
(
    QueryDefId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Schema VARCHAR(50) NOT NULL,
    Sql TEXT,
    MetaData TEXT,
    Description TEXT,
    SchemaVersion FLOAT8 NOT NULL,
    Flags INTEGER NOT NULL,
    CONSTRAINT PK_QueryDef PRIMARY KEY (QueryDefId),
    CONSTRAINT UQ_QueryDef UNIQUE (Container, Schema, Name)
);

CREATE TABLE query.CustomView
(
    CustomViewId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Schema VARCHAR(50) NOT NULL,
    QueryName VARCHAR(200) NOT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(200) NULL,
    CustomViewOwner INT NULL,
    Columns TEXT,
    Filter TEXT,
    Flags INTEGER NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, Schema, QueryName, CustomViewOwner, Name)
);

CREATE TABLE query.DbUserSchema
(
    DbUserSchemaId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,

    Container ENTITYID NOT NULL,
    UserSchemaName VARCHAR(50) NOT NULL,
    DbSchemaName VARCHAR(50) NOT NULL,
    DbContainer ENTITYID NULL,

    Editable BOOLEAN NOT NULL DEFAULT '0',
    MetaData TEXT NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container, UserSchemaName)
);

CREATE TABLE query.QuerySnapshotDef
(
    RowId SERIAL NOT NULL,
    QueryDefId INT NULL,

    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
    Container ENTITYID NOT NULL,
    Schema VARCHAR(50) NOT NULL,
    Name VARCHAR(200) NOT NULL,
    Columns TEXT,
    Filter TEXT,
    LastUpdated TIMESTAMP NULL,
    NextUpdate TIMESTAMP NULL,
    UpdateDelay INT DEFAULT 0,
    QueryTableName VARCHAR(200) NULL,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

/* query-9.20-9.30.sql */

-- Support other DataSources in external schemas (e.g., SAS, other PostgreSQL servers, etc.)
ALTER TABLE query.DbUserSchema ADD COLUMN DataSource VARCHAR(50) NOT NULL;

-- Remove unused column
ALTER TABLE query.DbUserSchema DROP COLUMN DbContainer;

/* query-10.10-10.20.sql */

-- Rename table and column to use "external schema" terminology
ALTER TABLE query.DbUserSchema RENAME TO ExternalSchema;

ALTER TABLE query.ExternalSchema
    RENAME DbUserSchemaId TO ExternalSchemaId;

-- Add bit to determine whether to index or not (indexing is on by default)
ALTER TABLE query.ExternalSchema ADD
    COLUMN Indexable BOOLEAN NOT NULL DEFAULT TRUE;

-- Specifies the tables to expose in a schema:
--  Comma-separated list of table names specifies a subset of tables in the schema
--  '*' represents all tables
--  Empty represents no tables (not very useful, of course...)
ALTER TABLE query.ExternalSchema ADD
    COLUMN Tables VARCHAR(8000) NOT NULL DEFAULT '*';

/* query-10.12-10.13.sql */

-- Switch to case-insensitive unique index... and rename it to match the new table name 
ALTER TABLE query.ExternalSchema DROP CONSTRAINT UQ_DbUserSchema;
CREATE UNIQUE INDEX UQ_ExternalSchema ON query.ExternalSchema (Container, LOWER(UserSchemaName));
