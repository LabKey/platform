/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
CREATE SCHEMA query;

SET search_path TO query, public;

CREATE TABLE query.QueryDef (
	QueryDefId SERIAL NOT NULL,
	EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NULL ,
    CreatedBy int NULL ,
    Modified TIMESTAMP NULL ,
    ModifiedBy int NULL ,

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

CREATE TABLE query.CustomView (
    CustomViewId SERIAL NOT NULL,
    EntityId ENTITYID NOT NULL,
    Created TIMESTAMP NOT NULL,
    CreatedBy int NULL,
    Modified TIMESTAMP NULL,
    ModifiedBy INT NULL,
	Schema VARCHAR(50) NOT NULL,
	QueryName VARCHAR(50) NOT NULL,

    Container ENTITYID NOT NULL,
    Name VARCHAR(50) NULL,
    CustomViewOwner int NULL,
    Columns TEXT,
    Filter TEXT,
    Flags INTEGER NOT NULL,
    CONSTRAINT PK_CustomView PRIMARY KEY (CustomViewId),
    CONSTRAINT UQ_CustomView UNIQUE (Container, Schema, QueryName, CustomViewOwner, Name)
    );