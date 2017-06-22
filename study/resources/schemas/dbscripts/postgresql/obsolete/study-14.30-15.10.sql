/*
 * Copyright (c) 2014-2017 LabKey Corporation
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

/* study-14.30-14.31.sql */

-- Add new tag column to study.dataset
ALTER TABLE study.dataset ADD COLUMN Tag VARCHAR(1000);

/* study-14.31-14.32.sql */

-- Add new skip query validation column to study.study
ALTER TABLE study.Study ADD COLUMN ValidateQueriesAfterImport BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE study.Study SET ValidateQueriesAfterImport = TRUE WHERE AllowReload = TRUE;

/* study-14.32-14.33.sql */

ALTER TABLE study.Study ADD COLUMN ShareVisitDefinitions BOOLEAN NOT NULL DEFAULT false;

/* study-14.33-14.34.sql */

SELECT core.fn_dropifexists ('Participant', 'study', 'CONSTRAINT', 'FK_CurrentSiteId_Site');
SELECT core.fn_dropifexists ('Participant', 'study', 'CONSTRAINT', 'FK_EnrollmentSiteId_Site');
SELECT core.fn_dropifexists ('SampleRequest', 'study', 'CONSTRAINT', 'FK_SampleRequest_Site');
SELECT core.fn_dropifexists ('SampleRequestRequirement', 'study', 'CONSTRAINT', 'FK_SampleRequestRequirement_Site');

-- Note: migrateSpecimenTypeAndLocationTables() drops these tables: study.SpecimenAdditive, study.SpecimenDerivative, study.SpecimenPrimaryType, study.site;
SELECT core.executeJavaUpgradeCode('migrateSpecimenTypeAndLocationTables');

/* study-14.34-14.35.sql */

ALTER TABLE study.StudySnapshot ADD COLUMN Type VARCHAR(10);
SELECT core.executeJavaUpgradeCode('upgradeStudySnapshotsType');