/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

/* study-12.20-12.21.sql */

-- Default to random offset, 1 - 365
ALTER TABLE study.Participant ADD COLUMN DateOffset INT NOT NULL DEFAULT CAST((RANDOM() * 364 + 1) AS INT);

-- Nullable... random alternate IDs are set via code
ALTER TABLE study.Participant ADD COLUMN AlternateId VARCHAR(32) NULL;

/* study-12.22-12.23.sql */

ALTER TABLE study.Cohort ADD COLUMN Enrolled BOOLEAN NOT NULL DEFAULT TRUE;

/* study-12.23-12.24.sql */

-- Add columns to store an alternate ID "template", i.e., an optional prefix and number of digits to use when generating random alternate IDs
ALTER TABLE study.Study ADD AlternateIdPrefix VARCHAR(20) NULL;
ALTER TABLE study.Study ADD AlternateIdDigits INT NOT NULL DEFAULT 6;

/* study-12.24-12.25.sql */

SELECT core.executeJavaUpgradeCode('upgradeParticipantReport');

/* study-12.25-12.26.sql */

-- Change some Specimen fields to bigint
ALTER TABLE study.specimenevent ALTER COLUMN rowid TYPE bigint;
ALTER TABLE study.specimenevent ALTER COLUMN vialid TYPE bigint;
ALTER TABLE study.vial ALTER COLUMN rowid TYPE bigint;
ALTER TABLE study.vial ALTER COLUMN specimenid TYPE bigint;
ALTER TABLE study.specimen ALTER COLUMN rowid TYPE bigint;

/* study-12.26-12.27.sql */

-- History of all study snapshots (i.e., ancillary studies and published studies) and the settings used to generate
-- them. Rows are effectively owned by both the source and destination container; they remain as long as EITHER the
-- source or destination container exists. This table is used primarily to support nightly refresh of specimen data
-- (we need to save the protected settings, visit list, and participant list somewhere), but could easily support a
-- snapshot history feature.
CREATE TABLE study.StudySnapshot
(
    RowId SERIAL,
    Source ENTITYID NULL,       -- Source study container; null if this study has been deleted
    Destination ENTITYID NULL,  -- Destination study container; null if this study has been deleted
    CreatedBy USERID,
    Created TIMESTAMP,

    Refresh BOOLEAN NOT NULL,   -- Included in settings, but separate column allows quick filtering
    Settings TEXT,

    CONSTRAINT PK_StudySnapshot PRIMARY KEY (RowId)
);

CREATE INDEX IX_StudySnapshot_Source ON study.StudySnapshot(Source);
CREATE INDEX IX_StudySnapshot_Destination ON study.StudySnapshot(Destination, RowId);

ALTER TABLE study.Study
    ADD StudySnapshot INT NULL,
    ADD LastSpecimenLoad TIMESTAMP NULL;  -- Helps determine whether a specimen refresh is needed

CREATE INDEX IX_Study_StudySnapshot ON study.Study(StudySnapshot);

/* study-12.27-12.28.sql */

-- Additional fields in Event and Vial tables
ALTER TABLE study.Vial ADD LatestComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestQualityComments VARCHAR(500);
ALTER TABLE study.Vial ADD LatestDeviationCode1 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode2 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestDeviationCode3 VARCHAR(50);
ALTER TABLE study.Vial ADD LatestConcentration REAL;
ALTER TABLE study.Vial ADD LatestIntegrity REAL;
ALTER TABLE study.Vial ADD LatestRatio REAL;
ALTER TABLE study.Vial ADD LatestYield REAL;

ALTER TABLE study.SpecimenEvent ALTER COLUMN Comments TYPE VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD QualityComments VARCHAR(500);
ALTER TABLE study.SpecimenEvent ADD DeviationCode1 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode2 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD DeviationCode3 VARCHAR(50);
ALTER TABLE study.SpecimenEvent ADD Concentration REAL;
ALTER TABLE study.SpecimenEvent ADD Integrity REAL;
ALTER TABLE study.SpecimenEvent ADD Ratio REAL;
ALTER TABLE study.SpecimenEvent ADD Yield REAL;

/* study-12.28-12.29.sql */

ALTER TABLE study.Visit ADD SequenceNumHandling VARCHAR(32) NULL;

/* study-12.29-12.291.sql */

ALTER TABLE study.SpecimenEvent ALTER COLUMN TubeType TYPE VARCHAR(64);
ALTER TABLE study.Vial ALTER COLUMN TubeType TYPE VARCHAR(64);

/* study-12.291-12.292.sql */

ALTER TABLE study.Vial ADD freezer VARCHAR(200);
ALTER TABLE study.Vial ADD fr_container VARCHAR(200);
ALTER TABLE study.Vial ADD fr_position VARCHAR(200);
ALTER TABLE study.Vial ADD fr_level1 VARCHAR(200);
ALTER TABLE study.Vial ADD fr_level2 VARCHAR(200);