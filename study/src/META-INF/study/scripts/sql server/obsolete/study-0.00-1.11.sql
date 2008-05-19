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

-- Tables and views used for Study module

EXEC sp_addapprole 'study', 'password'
GO

if NOT EXISTS (select * from systypes where name ='LSIDtype')
    exec sp_addtype 'LSIDtype', 'nvarchar(300)'
GO

CREATE TABLE study.Study
    (
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Study PRIMARY KEY (Container)
    )
GO

CREATE TABLE study.Site
    (
    SiteId INT NOT NULL,
    Label NVARCHAR(200) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Site PRIMARY KEY CLUSTERED (Container,SiteId)
    )
GO

CREATE TABLE study.Visit
    (
    VisitId INT NOT NULL,
    Label NVARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    Container ENTITYID NOT NULL
    CONSTRAINT PK_Visit PRIMARY KEY CLUSTERED (Container,VisitId)
    )
GO

CREATE TABLE study.VisitMap
    (
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,	-- FK
    DataSetId INT NOT NULL,	-- FK
    IsRequired BIT NOT NULL DEFAULT 1
    CONSTRAINT PK_VisitMap PRIMARY KEY CLUSTERED (Container,VisitId,DataSetId)
    )
GO

CREATE TABLE study.DataSet -- AKA CRF or Assay
   (
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI NVARCHAR(200) NULL,
    Label NVARCHAR(200) NULL,
    Category NVARCHAR(200) NULL
    CONSTRAINT PK_DataSet PRIMARY KEY CLUSTERED (Container,DataSetId)
   )
GO