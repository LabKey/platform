/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
EXEC sp_addapprole 'biotrue', 'password'
GO

CREATE TABLE biotrue.server
(
    RowId INT IDENTITY(1,1),
    Name NVARCHAR(256) NOT NULL,
    Container ENTITYID NOT NULL,
    WsdlURL NVARCHAR(1024) NULL,
    ServiceNamespaceURI NVARCHAR(256) NULL,
    ServiceLocalPart NVARCHAR(256) NULL,
    UserName NVARCHAR(256) NULL,
    Password NVARCHAR(256) NULL,
    PhysicalRoot NVARCHAR(500) NULL,
    SyncInterval INT NOT NULL DEFAULT 0,
    NextSync DATETIME,

    CONSTRAINT PK_Server PRIMARY KEY(RowId),
    CONSTRAINT UQ_Server UNIQUE(Container, Name)
);
GO

CREATE TABLE biotrue.entity
(
    RowId INT IDENTITY(1,1),
    ServerId INT NOT NULL,
    ParentId INT NOT NULL,
    BioTrue_Id NVARCHAR(256) NOT NULL,
    BioTrue_Type NVARCHAR(256) NULL,
    BioTrue_Name NVARCHAR(256) NOT NULL,
    PhysicalName NVARCHAR(256),
    CONSTRAINT PK_Entity PRIMARY KEY(RowId),
    CONSTRAINT FK_Entity_Server FOREIGN KEY(ServerId) REFERENCES biotrue.Server(RowId),
    CONSTRAINT UQ_Entity_BioTrue_Parent_Id UNIQUE(ServerId, ParentId, BioTrue_Id)
);
GO

CREATE TABLE biotrue.task
(
    RowId INT IDENTITY(1,1),
    ServerId INT NOT NULL,
    EntityId INT NOT NULL,
    Operation NVARCHAR(50) NOT NULL,
    Started DATETIME NULL,
    CONSTRAINT PK_Task PRIMARY KEY(RowId)
);
GO

CREATE INDEX IX_Task_Server ON biotrue.task(ServerId, RowId);
CREATE INDEX IX_Task_Entity ON biotrue.task(EntityId);
GO
