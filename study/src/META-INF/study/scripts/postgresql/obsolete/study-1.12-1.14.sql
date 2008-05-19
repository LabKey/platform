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

DROP SCHEMA study CASCADE;
CREATE SCHEMA study;

SET search_path TO study, public;

CREATE TABLE Study
    (
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Study PRIMARY KEY (Container)
    );

CREATE TABLE Site
    (
    SiteId INT NOT NULL,
    Label VARCHAR(200) NULL,
    Container ENTITYID NOT NULL,
    CONSTRAINT PK_Site PRIMARY KEY (Container,SiteId)
    );

CREATE TABLE Visit
    (
    VisitId INT NOT NULL,
    Label VARCHAR(200) NULL,
    TypeCode CHAR(1) NULL,
    Container ENTITYID NOT NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    CONSTRAINT PK_Visit PRIMARY KEY (Container,VisitId)
    );

CREATE TABLE VisitMap
    (
    Container ENTITYID NOT NULL,
    VisitId INT NOT NULL,	-- FK
    DataSetId INT NOT NULL,	-- FK
    IsRequired BOOLEAN NOT NULL DEFAULT '1',
    CONSTRAINT PK_VisitMap PRIMARY KEY (Container,VisitId,DataSetId)
    );

CREATE TABLE DataSet -- AKA CRF or Assay
   (
    Container ENTITYID NOT NULL,
    DataSetId INT NOT NULL,
    TypeURI VARCHAR(200) NULL,
    Label VARCHAR(200) NULL,
    Category VARCHAR(200) NULL,
    ShowByDefault BOOLEAN NOT NULL DEFAULT '1',
    DisplayOrder INT NOT NULL DEFAULT 0,
    CONSTRAINT PK_DataSet PRIMARY KEY (Container,DataSetId)
   );


-- ParticipantId is not a sequence, we assume these are externally defined
CREATE TABLE study.Participant
(
	Container ENTITYID NOT NULL,
	ParticipantId INT4 NOT NULL,

	CONSTRAINT PK_Participant PRIMARY KEY (Container, ParticipantId)
);


CREATE TABLE study.ParticipantDataset
(
	Container ENTITYID NOT NULL,
	ParticipantId INT4 NOT NULL,
	VisitId INT4 NULL,
	DatasetId INT4 NOT NULL,
	URI varchar(200) NOT NULL,
	CONSTRAINT PK_ParticipantDataset PRIMARY KEY (URI),
	CONSTRAINT AK_ParticipantDataset UNIQUE (Container, DatasetId, VisitId, ParticipantId)
);
CLUSTER  AK_ParticipantDataset ON study.ParticipantDataset;

-- two indexes for query optimization
CREATE INDEX IDX_ParticipantDatasetByVisit ON study.ParticipantDataset (Container, DatasetId, VisitId, ParticipantId, URI);
CREATE INDEX IDX_ParticipantDatasetByParticipant ON study.ParticipantDataset (Container, ParticipantId, DatasetId, VisitId, URI);