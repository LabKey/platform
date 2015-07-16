/*
 * Copyright (c) 2015 LabKey Corporation
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

SELECT core.fn_dropifexists ('Participant', 'study', 'CONSTRAINT', 'FK_CurrentSiteId_Site');
SELECT core.fn_dropifexists ('Participant', 'study', 'CONSTRAINT', 'FK_EnrollmentSiteId_Site');
SELECT core.fn_dropifexists ('SampleRequest', 'study', 'CONSTRAINT', 'FK_SampleRequest_Site');
SELECT core.fn_dropifexists ('SampleRequestRequirement', 'study', 'CONSTRAINT', 'FK_SampleRequestRequirement_Site');

SELECT core.executeJavaUpgradeCode('migrateSpecimenTypeAndLocationTables');

-- These are dropped in the upgrade script
--DROP TABLE study.SpecimenAdditive;
--DROP TABLE study.SpecimenDerivative;
--DROP TABLE study.SpecimenPrimaryType;
--DROP TABLE study.site;
