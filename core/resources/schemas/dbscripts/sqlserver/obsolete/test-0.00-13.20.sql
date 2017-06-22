/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

/* test-0.00-12.10.sql */

CREATE SCHEMA test;
GO

CREATE TABLE test.TestTable
(
    _ts TIMESTAMP,
    EntityId ENTITYID DEFAULT NEWID(),
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,

    Container ENTITYID,            --container/path
    Text NVARCHAR(195),        --filename

    IntNull INT NULL,
    IntNotNull INT NOT NULL,
    DatetimeNull DATETIME NULL,
    DatetimeNotNull DATETIME NOT NULL,
    RealNull REAL NULL,
    BitNull Bit NULL,
    BitNotNull Bit NOT NULL,

    CONSTRAINT PK_TestTable PRIMARY KEY (RowId)
);

/* test-13.10-13.20.sql */

CREATE TABLE test.TestTable2
(
    _ts TIMESTAMP,
    EntityId ENTITYID DEFAULT NEWID(),
    RowId INT IDENTITY(1,1),
    CreatedBy USERID,
    Created DATETIME,

    Container ENTITYID,            --container/path
    Text NVARCHAR(195),        --filename

    IntNull INT NULL,
    IntNotNull INT NOT NULL,
    DatetimeNull DATETIME NULL,
    DatetimeNotNull DATETIME NOT NULL,
    RealNull REAL NULL,
    BitNull Bit NULL,
    BitNotNull Bit NOT NULL,

    CONSTRAINT PK_TestTable2 PRIMARY KEY (Container,Text)
);