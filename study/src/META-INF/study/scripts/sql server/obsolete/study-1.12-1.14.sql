/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

IF NOT EXISTS (SELECT * FROM sysusers WHERE name ='study')
    EXEC sp_addapprole 'study', 'password'
GO

IF NOT EXISTS (select * from systypes where name ='LSIDtype')
    EXEC sp_addtype 'LSIDtype', 'nvarchar(300)'
GO

-- These should get dropped with the table
--DROP INDEX study.ParticipantDataset.IDX_ParticipantDatasetByVisit;
--DROP INDEX study.ParticipantDataset.IDX_ParticipantDatasetByParticipant;
--GO

IF OBJECT_ID('study.Study','U') IS NOT NULL
    DROP TABLE study.Study;
IF OBJECT_ID('study.Site','U') IS NOT NULL
    DROP TABLE study.Site;
IF OBJECT_ID('study.Visit','U') IS NOT NULL
    DROP TABLE study.Visit;
IF OBJECT_ID('study.VisitMap','U') IS NOT NULL
    DROP TABLE study.VisitMap;
IF OBJECT_ID('study.DataSet','U') IS NOT NULL
    DROP TABLE study.DataSet;
IF OBJECT_ID('study.Participant','U') IS NOT NULL
    DROP TABLE study.Participant
IF OBJECT_ID('study.ParticipantDataset','U') IS NOT NULL
    DROP TABLE study.ParticipantDataset;
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
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
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
    ShowByDefault BIT NOT NULL DEFAULT 1,
    DisplayOrder INT NOT NULL DEFAULT 0,
    Category NVARCHAR(200) NULL
    CONSTRAINT PK_DataSet PRIMARY KEY CLUSTERED (Container,DataSetId)
   )
GO

-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId INT NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
)
go


CREATE TABLE study.ParticipantDataset
(
	Container ENTITYID NOT NULL,
	ParticipantId INT NOT NULL,
	VisitId INT NULL,
	DatasetId INT NOT NULL,
	URI varchar(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (URI),
	CONSTRAINT AK_ParticipantDataset UNIQUE CLUSTERED (Container, DatasetId, VisitId, ParticipantId)
)
go

-- two indexes for query optimization
CREATE INDEX IDX_ParticipantDatasetByVisit ON study.ParticipantDataset (Container, DatasetId, VisitId, ParticipantId, URI)
CREATE INDEX IDX_ParticipantDatasetByParticipant ON study.ParticipantDataset (Container, ParticipantId, DatasetId, VisitId, URI)
go