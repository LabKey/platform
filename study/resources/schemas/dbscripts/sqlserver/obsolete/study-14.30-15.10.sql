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
ALTER TABLE study.dataset ADD Tag VARCHAR(1000);

/* study-14.31-14.32.sql */

-- Add new skip query validation column to study.study
ALTER TABLE study.Study ADD ValidateQueriesAfterImport BIT NOT NULL DEFAULT 0;
GO
UPDATE study.Study SET ValidateQueriesAfterImport = 1 WHERE AllowReload = 1;

/* study-14.32-14.33.sql */

ALTER TABLE study.Study ADD ShareVisitDefinitions BIT NOT NULL DEFAULT 0;

/* study-14.33-14.34.sql */

EXEC core.fn_dropifexists 'Participant', 'study', 'CONSTRAINT', 'FK_CurrentSiteId_Site';
EXEC core.fn_dropifexists 'Participant', 'study', 'CONSTRAINT', 'FK_EnrollmentSiteId_Site';
EXEC core.fn_dropifexists 'SampleRequest', 'study', 'CONSTRAINT', 'FK_SampleRequest_Site';
EXEC core.fn_dropifexists 'SampleRequestRequirement', 'study', 'CONSTRAINT', 'FK_SampleRequestRequirement_Site';

-- Note: migrateSpecimenTypeAndLocationTables() drops these tables: study.SpecimenAdditive, study.SpecimenDerivative, study.SpecimenPrimaryType, study.site;
EXEC core.executeJavaUpgradeCode 'migrateSpecimenTypeAndLocationTables';

/* study-14.34-14.35.sql */

ALTER TABLE study.StudySnapshot ADD Type VARCHAR(10);
GO
EXEC core.executeJavaUpgradeCode 'upgradeStudySnapshotsType';