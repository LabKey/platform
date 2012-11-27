/*
 * Copyright (c) 2012 LabKey Corporation
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

/* study-12.21-12.22.sql */

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD DateOffset INT NOT NULL DEFAULT ABS(CHECKSUM(NEWID())) % 364 + 1;

-- Random alternate IDs are set via code
ALTER TABLE study.Participant ADD AlternateId VARCHAR(32) NULL;

/* study-12.22-12.23.sql */

ALTER TABLE study.Cohort ADD Enrolled BIT NOT NULL DEFAULT 1;

/* study-12.23-12.24.sql */

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;

/* study-12.24-12.25.sql */

EXEC core.executeJavaUpgradeCode 'upgradeParticipantReport';

/* study-12.25-12.26.sql */

-- Change some Specimen fields to bigint
DROP INDEX study.specimenevent.IX_SpecimenEvent_SpecimenId;
DROP INDEX study.vial.IX_Vial_SpecimenId;

ALTER TABLE study.specimenevent DROP CONSTRAINT FK_SpecimensEvents_Specimens;
ALTER TABLE study.vial DROP CONSTRAINT FK_Vial_Specimen;

ALTER TABLE study.specimenevent DROP CONSTRAINT PK_SpecimensEvents;
ALTER TABLE study.specimen DROP CONSTRAINT PK_Specimen;
ALTER TABLE study.vial DROP CONSTRAINT PK_Specimens;

ALTER TABLE study.specimenevent ALTER COLUMN rowid bigint NOT NULL;
ALTER TABLE study.specimenevent ALTER COLUMN vialid bigint NOT NULL;
ALTER TABLE study.vial ALTER COLUMN rowid bigint NOT NULL;
ALTER TABLE study.vial ALTER COLUMN specimenid bigint NOT NULL;
ALTER TABLE study.specimen ALTER COLUMN rowid bigint NOT NULL;

ALTER TABLE study.vial ADD CONSTRAINT PK_Specimens PRIMARY KEY (rowid);
ALTER TABLE study.specimen ADD CONSTRAINT PK_Specimen PRIMARY KEY (rowid);
ALTER TABLE study.specimenevent ADD CONSTRAINT PK_SpecimensEvents PRIMARY KEY (rowid);

ALTER TABLE study.vial ADD CONSTRAINT FK_Vial_Specimen FOREIGN KEY (SpecimenId) REFERENCES study.Specimen(RowId);
ALTER TABLE study.specimenevent ADD CONSTRAINT FK_SpecimensEvents_Specimens FOREIGN KEY (VialId) REFERENCES study.Vial(RowId);

CREATE INDEX IX_Vial_SpecimenId ON study.vial(SpecimenId);
CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(VialId);

/* study-12.26-12.27.sql */

-- History of all study snapshots (i.e., ancillary studies and published studies) and the settings used to generate
-- them. Rows are effectively owned by both the source and destination container; they remain as long as EITHER the
-- source or destination container exists. This table is used primarily to support nightly refresh of specimen data
-- (we need to save the protected settings, visit list, and participant list somewhere), but could easily support a
-- snapshot history feature.
CREATE TABLE study.StudySnapshot
(
    RowId INT IDENTITY(1,1),
    Source ENTITYID NULL,       -- Source study container; null if this study has been deleted
    Destination ENTITYID NULL,  -- Destination study container; null if this study has been deleted
    CreatedBy USERID,
    Created DATETIME,

    Refresh BIT NOT NULL,       -- Included in settings, but separate column allows quick filtering
    Settings TEXT,

    CONSTRAINT PK_StudySnapshot PRIMARY KEY (RowId)
);

CREATE INDEX IX_StudySnapshot_Source ON study.StudySnapshot(Source);
CREATE INDEX IX_StudySnapshot_Destination ON study.StudySnapshot(Destination, RowId);

ALTER TABLE study.Study ADD
    StudySnapshot INT NULL,
    LastSpecimenLoad DATETIME NULL;  -- Helps determine whether a specimen refresh is needed

CREATE INDEX IX_Study_StudySnapshot ON study.Study(StudySnapshot);

/* study-12.27-12.28.sql */

-- Additional fields in Event and Vial tables
ALTER TABLE study.Vial ADD LatestComments VARCHAR(500) NULL;
ALTER TABLE study.Vial ADD LatestQualityComments VARCHAR(500) NULL;
ALTER TABLE study.Vial ADD LatestDeviationCode1 VARCHAR(50) NULL;
ALTER TABLE study.Vial ADD LatestDeviationCode2 VARCHAR(50) NULL;
ALTER TABLE study.Vial ADD LatestDeviationCode3 VARCHAR(50) NULL;
ALTER TABLE study.Vial ADD LatestConcentration REAL NULL;
ALTER TABLE study.Vial ADD LatestIntegrity REAL NULL;
ALTER TABLE study.Vial ADD LatestRatio REAL NULL;
ALTER TABLE study.Vial ADD LatestYield REAL NULL;

ALTER TABLE study.SpecimenEvent ALTER COLUMN Comments VARCHAR(500) NULL;
ALTER TABLE study.SpecimenEvent ADD QualityComments VARCHAR(500) NULL;
ALTER TABLE study.SpecimenEvent ADD DeviationCode1 VARCHAR(50) NULL;
ALTER TABLE study.SpecimenEvent ADD DeviationCode2 VARCHAR(50) NULL;
ALTER TABLE study.SpecimenEvent ADD DeviationCode3 VARCHAR(50) NULL;
ALTER TABLE study.SpecimenEvent ADD Concentration REAL NULL;
ALTER TABLE study.SpecimenEvent ADD Integrity REAL NULL;
ALTER TABLE study.SpecimenEvent ADD Ratio REAL NULL;
ALTER TABLE study.SpecimenEvent ADD Yield REAL NULL;

/* study-12.28-12.29.sql */

ALTER TABLE study.Visit ADD SequenceNumHandling VARCHAR(32) NULL;

/* study-12.29-12.291.sql */

ALTER TABLE study.SpecimenEvent ALTER COLUMN TubeType NVARCHAR(64);
ALTER TABLE study.Vial ALTER COLUMN TubeType NVARCHAR(64);

/* study-12.291-12.292.sql */

ALTER TABLE study.Vial ADD freezer VARCHAR(200) NULL;
ALTER TABLE study.Vial ADD fr_container VARCHAR(200) NULL;
ALTER TABLE study.Vial ADD fr_position VARCHAR(200) NULL;
ALTER TABLE study.Vial ADD fr_level1 VARCHAR(200) NULL;
ALTER TABLE study.Vial ADD fr_level2 VARCHAR(200) NULL;