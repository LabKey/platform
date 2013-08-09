/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
