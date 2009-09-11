/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
/* query-1.50-1.60.sql */

EXEC sp_addapprole 'query', 'password';

CREATE TABLE query.QueryDef (
	QueryDefId INT IDENTITY(1, 1) NOT NULL,
	EntityId UNIQUEIDENTIFIER NOT NULL,
    Created DATETIME NULL ,
    CreatedBy INT NULL ,
    Modified DATETIME NULL ,
    ModifiedBy INT NULL ,

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
    );

CREATE TABLE query.CustomView (
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
    );

/* query-1.60-1.70.sql */

CREATE TABLE query.DbUserSchema (
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
);

/* query-1.70-2.00.sql */

UPDATE query.querydef SET metadata = REPLACE(CAST(metadata AS VARCHAR(8000)), 'http://cpas.fhcrc.org/data/xml', 'http://labkey.org/data/xml');