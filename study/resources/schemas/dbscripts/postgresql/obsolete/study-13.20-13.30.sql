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
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignimmunogentypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignGenes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesigngenes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignRoutes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignroutes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSubTypes
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsubtypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignSampleTypes
(
  Name VARCHAR(200) NOT NULL,
  PrimaryType VARCHAR(200) NOT NULL,
  ShortSampleCode VARCHAR(2) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignsampletypes PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignUnits
(
  Name VARCHAR(3) NOT NULL, -- storage name
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignAssays
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Description TEXT,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignassays PRIMARY KEY (Container, Name)
);

CREATE TABLE study.StudyDesignLabs
(
  Name VARCHAR(200) NOT NULL,
  Label VARCHAR(200) NOT NULL,
  Inactive BOOLEAN NOT NULL DEFAULT FALSE,

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT pk_studydesignlabs PRIMARY KEY (Container, Name)
);

/* study-13.21-13.22.sql */

-- Create an owner colunn to represent shared or private participant categories
ALTER TABLE study.ParticipantCategory ADD COLUMN OwnerId USERID NOT NULL DEFAULT -1;
UPDATE study.ParticipantCategory SET OwnerId = CreatedBy WHERE NOT shared;

ALTER TABLE study.ParticipantCategory DROP CONSTRAINT uq_label_container;
ALTER TABLE study.ParticipantCategory DROP COLUMN shared;
ALTER TABLE study.ParticipantCategory ADD CONSTRAINT uq_label_container_owner UNIQUE(Label, Container, OwnerId);

/* study-13.22-13.23.sql */

-- Default the template type to nab if one isn't explicitly set
UPDATE study.Plate SET Type = 'NAb' WHERE Type IS NULL;

/* study-13.23-13.24.sql */

SELECT core.executeJavaUpgradeCode('upgradeExternalReports');