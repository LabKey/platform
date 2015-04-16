/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

CREATE TABLE query.ExternalSchema
(
    ExternalSchemaId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL,
    CreatedBy INT NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,

    Container ENTITYID NOT NULL,
    DataSource VARCHAR(50) NOT NULL,
    UserSchemaName VARCHAR(50) NOT NULL,
    DbSchemaName VARCHAR(50) NOT NULL,

    Editable BOOLEAN NOT NULL DEFAULT '0',
    MetaData TEXT NULL,
    Indexable BOOLEAN NOT NULL DEFAULT TRUE,
    Tables VARCHAR(8000) NOT NULL DEFAULT '*',

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(ExternalSchemaId)
);

CREATE UNIQUE INDEX UQ_ExternalSchema ON query.ExternalSchema (Container, LOWER(UserSchemaName));

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
    QueryTableContainer ENTITYID,
    ParticipantGroups TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

/* query-12.20-12.30.sql */

ALTER TABLE query.querydef ALTER COLUMN schema TYPE VARCHAR(200);