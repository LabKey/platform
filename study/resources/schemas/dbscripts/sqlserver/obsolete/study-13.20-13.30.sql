/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

/* study-13.20-13.21.sql */

CREATE TABLE study.StudyDesignImmunogenTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignimmunogentypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignGenes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesigngenes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignRoutes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignroutes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSubTypes
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsubtypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSampleTypes
(
  Name NVARCHAR(200) NOT NULL,
  PrimaryType NVARCHAR(200) NOT NULL,
  ShortSampleCode NVARCHAR(2) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsampletypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignUnits
(
  Name NVARCHAR(3) NOT NULL, -- storage name
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignAssays
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Description TEXT,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignassays PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignLabs
(
  Name NVARCHAR(200) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Inactive BIT NOT NULL DEFAULT 0,

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignlabs PRIMARY KEY (Container, Name)
);

/* study-13.21-13.22.sql */

-- Create an owner colunn to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD OwnerId USERID NOT NULL DEFAULT -1;
GO

UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE Shared <> 1;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN Shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);

/* study-13.22-13.23.sql */

-- Default the template type to nab if one isn't explicitly set
UPDATE study.Plate SET Type = 'NAb' WHERE Type IS NULL;

/* study-13.23-13.24.sql */

EXEC core.executeJavaUpgradeCode 'upgradeExternalReports';