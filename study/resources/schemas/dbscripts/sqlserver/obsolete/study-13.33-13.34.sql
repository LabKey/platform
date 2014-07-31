/*
 * Copyright (c) 2013-2014 LabKey Corporation
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