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

/* study-13.30-13.31.sql */

ALTER TABLE study.Visit ADD COLUMN Description TEXT;

/* study-13.31-13.32.sql */

CREATE SCHEMA specimenTables;

/* study-13.32-13.33.sql */

SELECT core.executeJavaUpgradeCode('migrateSpecimenTables');

DROP TABLE study.specimenevent;
DROP TABLE study.vial;
DROP TABLE study.specimen;

/* study-13.33-13.34.sql */

CREATE SCHEMA studydesign;

CREATE TABLE study.TreatmentVisitMap
(
  CohortId INT NOT NULL,
  TreatmentId INT NOT NULL,
  VisitId INT NOT NULL,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_CohortId_TreatmentId_VisitId PRIMARY KEY (CohortId, TreatmentId, VisitId, Container)
);

CREATE TABLE study.Objective
(
  RowId SERIAL,
  Label VARCHAR(200) NOT NULL,
  Type VARCHAR(200),
  Description TEXT,
  DescriptionRendererType VARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS',

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Objective PRIMARY KEY (RowId)
);

CREATE TABLE study.VisitTag
(
  VisitRowId INT,

  Tag VARCHAR(200) NOT NULL,
  Description VARCHAR(200),

  CreatedBy USERID,
  Created TIMESTAMP,
  ModifiedBy USERID,
  Modified TIMESTAMP,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_VisitRowId_Tag_Container PRIMARY KEY (VisitRowId, Tag, Container)
  --CONSTRAINT FK_Visit_VisitRowId FOREIGN KEY (VisitRowId, Container) REFERENCES study.Visit (RowId, Container)
);

-- new fields to add to existing cohort table
ALTER TABLE study.Cohort ADD COLUMN SubjectCount INT;
ALTER TABLE study.Cohort ADD COLUMN Description TEXT;

-- new fields to add to existing study properties table
ALTER TABLE study.Study ADD COLUMN Species VARCHAR(200);
ALTER TABLE study.Study ADD COLUMN EndDate TIMESTAMP;
ALTER TABLE study.Study ADD COLUMN AssayPlan TEXT;

-- new fields to add to existing visit table, default SequenceNumTarget to SequenceNumMin
ALTER TABLE study.Visit ADD COLUMN SequenceNumTarget NUMERIC(15,4) NOT NULL DEFAULT 0;
-- UPDATE study.Visit SET SequenceNumTarget = SequenceNumMin;  #19819: leave upgraded visits defaulting to 0

-- new fields to add to the existing site/location table
ALTER TABLE study.Site ADD COLUMN Description VARCHAR(500);
ALTER TABLE study.Site ADD COLUMN StreetAddress VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN City VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN GoverningDistrict VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN Country VARCHAR(200);
ALTER TABLE study.Site ADD COLUMN PostalArea VARCHAR(50);

-- new tables for storing the assay schedule information
CREATE TABLE study.AssaySpecimen
(
  RowId SERIAL NOT NULL,

  Container ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,

  AssayName VARCHAR(200),
  Description VARCHAR(200),
  LocationId INTEGER,
  Source VARCHAR(20),
  TubeType VARCHAR(64),
  PrimaryTypeId INTEGER,
  DerivativeTypeId INTEGER,

  CONSTRAINT PK_AssaySpecimen PRIMARY KEY (Container, RowId)
);

CREATE TABLE study.AssaySpecimenVisit
(
  RowId SERIAL NOT NULL,

  Container ENTITYID NOT NULL,
  Created TIMESTAMP,
  CreatedBy USERID,
  Modified TIMESTAMP,
  ModifiedBy USERID,

  VisitId INTEGER,
  AssaySpecimenId INTEGER,

  CONSTRAINT PK_AssaySpecimenVisit PRIMARY KEY (Container, RowId)
);
CREATE UNIQUE INDEX UQ_VisitAssaySpecimen ON study.AssaySpecimenVisit(Container, VisitId, AssaySpecimenId);
CREATE UNIQUE INDEX UQ_AssaySpecimenVisit ON study.AssaySpecimenVisit(Container, AssaySpecimenId, VisitId);

/* study-13.34-13.35.sql */

SELECT core.executeJavaUpgradeCode('moveDefaultFormatProperties');

/* study-13.36-13.37.sql */

SELECT core.executeJavaUpgradeCode('migrateProvisionedDatasetTables141');

/* study-13.37-13.38.sql */

ALTER TABLE study.Study ADD COLUMN ShareDatasetDefinitions BOOLEAN NOT NULL DEFAULT false;

/* study-13.38-13.39.sql */

ALTER TABLE study.AssaySpecimen ADD COLUMN Lab VARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD COLUMN SampleType VARCHAR(200);

/* study-13.40-13.41.sql */

SELECT core.executeJavaUpgradeCode('migrateSpecimenDrawTimeStamp');

/* study-13.41-13.42.sql */

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name TYPE VARCHAR(5);

/* study-13.42-13.43.sql */

ALTER TABLE study.visit RENAME COLUMN SequenceNumTarget TO ProtocolDay;
UPDATE study.visit SV SET ProtocolDay = Round((SV.SequenceNumMax + SV.SequenceNumMin)/2)
FROM study.study SS
WHERE SS.Container = SV.Container AND SS.TimePointType = 'DATE';