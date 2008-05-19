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