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
    Name VARCHAR(50) NOT NULL,
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
    QueryName VARCHAR(50) NOT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NULL,
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
    DbSchemaName VARCHAR(50) NULL,
    DbContainer ENTITYID NULL,

    CONSTRAINT PK_DbUserSchema PRIMARY KEY(DbUserSchemaId),
    CONSTRAINT UQ_DbUserSchema UNIQUE(Container, UserSchemaName)
);

ALTER TABLE query.dbuserschema ADD COLUMN editable BOOLEAN NOT NULL DEFAULT '0';
ALTER TABLE query.dbuserschema ADD COLUMN metadata TEXT NULL;
ALTER TABLE query.customview ALTER COLUMN queryname TYPE VARCHAR(200);
ALTER TABLE query.dbuserschema ALTER COLUMN dbschemaname SET NOT NULL;

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
    Name VARCHAR(50) NOT NULL,
    Columns TEXT,
    Filter TEXT,

    CONSTRAINT PK_RowId PRIMARY KEY (RowId),
    CONSTRAINT FK_QuerySnapshotDef_QueryDefId FOREIGN KEY (QueryDefId) REFERENCES query.QueryDef (QueryDefId)
);

ALTER TABLE query.QueryDef ALTER COLUMN Name TYPE VARCHAR(200);
ALTER TABLE query.QuerySnapshotDef ALTER COLUMN Name TYPE VARCHAR(200);
ALTER TABLE query.QuerySnapshotDef ADD COLUMN LastUpdated TIMESTAMP NULL;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN NextUpdate TIMESTAMP NULL;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN UpdateDelay INT DEFAULT 0;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN ViewName VARCHAR(50) NULL;
ALTER TABLE query.QuerySnapshotDef ADD COLUMN QueryTableName VARCHAR(200) NULL;
ALTER TABLE query.QuerySnapshotDef DROP COLUMN ViewName;

/* query-8.30-9.10.sql */

ALTER TABLE query.customview ALTER COLUMN name TYPE VARCHAR(200);