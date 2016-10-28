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

ALTER TABLE study.Visit ADD Description NTEXT;

/* study-13.31-13.32.sql */

GO

CREATE SCHEMA specimenTables;

/* study-13.32-13.33.sql */

EXEC core.executeJavaUpgradeCode 'migrateSpecimenTables';

DROP TABLE study.specimenevent;
DROP TABLE study.vial;
DROP TABLE study.specimen;

/* study-13.33-13.34.sql */
GO

CREATE SCHEMA studydesign;
GO

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
  RowId INT IDENTITY(1, 1) NOT NULL,
  Label NVARCHAR(200) NOT NULL,
  Type NVARCHAR(200),
  Description NTEXT,
  DescriptionRendererType NVARCHAR(50) NOT NULL DEFAULT 'TEXT_WITH_LINKS',

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_Objective PRIMARY KEY (RowId)
);

CREATE TABLE study.VisitTag
(
  VisitRowId INT,

  Tag NVARCHAR(200) NOT NULL,
  Description NVARCHAR(200),

  CreatedBy USERID,
  Created DATETIME,
  ModifiedBy USERID,
  Modified DATETIME,
  Container ENTITYID NOT NULL,

  CONSTRAINT PK_VisitRowId_Tag_Container PRIMARY KEY (VisitRowId, Tag, Container),
  --CONSTRAINT FK_Visit_VisitRowId FOREIGN KEY (VisitRowId, Container) REFERENCES study.Visit (RowId, Container)
);

-- new fields to add to existing cohort table
ALTER TABLE study.Cohort ADD SubjectCount INT;
ALTER TABLE study.Cohort ADD Description NTEXT;

-- new fields to add to existing study properties table
ALTER TABLE study.Study ADD Species NVARCHAR(200);
ALTER TABLE study.Study ADD EndDate DATETIME;
ALTER TABLE study.Study ADD AssayPlan NTEXT;

-- new fields to add to existing visit table, default SequenceNumTarget to SequenceNumMin
ALTER TABLE study.Visit ADD SequenceNumTarget NUMERIC(15,4) NOT NULL DEFAULT 0;
--GO
--UPDATE study.Visit SET SequenceNumTarget = SequenceNumMin;  #19819: leave upgraded visits defaulting to 0

-- new fields to add to the existing site/location table
ALTER TABLE study.Site ADD Description NVARCHAR(500);
ALTER TABLE study.Site ADD StreetAddress NVARCHAR(200);
ALTER TABLE study.Site ADD City NVARCHAR(200);
ALTER TABLE study.Site ADD GoverningDistrict NVARCHAR(200);
ALTER TABLE study.Site ADD Country NVARCHAR(200);
ALTER TABLE study.Site ADD PostalArea NVARCHAR(50);

-- new tables for storing the assay schedule information
CREATE TABLE study.AssaySpecimen
(
  RowId INT IDENTITY(1, 1) NOT NULL,

  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  AssayName NVARCHAR(200),
  Description NVARCHAR(200),
  LocationId INTEGER,
  Source NVARCHAR(20),
  TubeType NVARCHAR(64),
  PrimaryTypeId INTEGER,
  DerivativeTypeId INTEGER,

  CONSTRAINT PK_AssaySpecimen PRIMARY KEY (Container, RowId)
);

CREATE TABLE study.AssaySpecimenVisit
(
  RowId INT IDENTITY(1, 1) NOT NULL,

  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  VisitId INTEGER,
  AssaySpecimenId INTEGER,

  CONSTRAINT PK_AssaySpecimenVisit PRIMARY KEY (Container, RowId)
);
CREATE UNIQUE INDEX UQ_VisitAssaySpecimen ON study.AssaySpecimenVisit(Container, VisitId, AssaySpecimenId);
CREATE UNIQUE INDEX UQ_AssaySpecimenVisit ON study.AssaySpecimenVisit(Container, AssaySpecimenId, VisitId);

/* study-13.34-13.35.sql */

EXEC core.executeJavaUpgradeCode 'moveDefaultFormatProperties';

/* study-13.35-13.36.sql */

ALTER TABLE study.Study ALTER COLUMN Description NVARCHAR(MAX);

/* study-13.36-13.37.sql */

EXEC core.executeJavaUpgradeCode 'migrateProvisionedDatasetTables141';

/* study-13.37-13.38.sql */

ALTER TABLE study.Study ADD ShareDatasetDefinitions BIT NOT NULL DEFAULT 0;

/* study-13.38-13.39.sql */

ALTER TABLE study.AssaySpecimen ADD Lab NVARCHAR(200);
ALTER TABLE study.AssaySpecimen ADD SampleType NVARCHAR(200);

/* study-13.39-13.40.sql */

-- clustered indexes just contribute to deadlocks, we don't really need this one

ALTER TABLE study.DataSet DROP CONSTRAINT PK_DataSet;
GO

ALTER TABLE study.DataSet ADD CONSTRAINT PK_DataSet PRIMARY KEY (Container, DataSetId);

/* study-13.40-13.41.sql */

EXEC core.executeJavaUpgradeCode 'migrateSpecimenDrawTimeStamp';

/* study-13.41-13.42.sql */

-- Issue 19442: Change study.StudyDesignUnits “Name” field from 3 chars to 5 chars field length
ALTER TABLE study.StudyDesignUnits DROP CONSTRAINT pk_studydesignunits;
ALTER TABLE study.StudyDesignUnits ALTER COLUMN Name NVARCHAR(5) NOT NULL;
ALTER TABLE study.StudyDesignUnits ADD CONSTRAINT pk_studydesignunits PRIMARY KEY (Container, Name);

/* study-13.42-13.43.sql */

EXEC sp_rename 'study.visit.SequenceNumTarget', 'ProtocolDay', 'COLUMN';
GO

UPDATE study.Visit
SET ProtocolDay = Round((SequenceNumMax + SequenceNumMin)/2, 0)
FROM study.Study SS
WHERE SS.Container = study.Visit.Container AND SS.TimePointType = 'DATE';