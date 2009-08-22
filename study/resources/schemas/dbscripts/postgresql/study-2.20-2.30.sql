/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
ALTER TABLE study.Study
ADD DateBased Boolean DEFAULT false,
ADD StartDate TIMESTAMP;

UPDATE study.Study SET DateBased=false WHERE DateBased IS NULL;

ALTER TABLE study.ParticipantVisit
ADD Day int4;

ALTER TABLE study.Participant
ADD StartDate TIMESTAMP;

ALTER TABLE study.Dataset
ADD DemographicData Boolean DEFAULT false;

UPDATE study.Dataset SET DemographicData=false WHERE DemographicData IS NULL;

ALTER TABLE study.Study ADD StudySecurity Boolean DEFAULT false;

UPDATE study.Study SET StudySecurity=true;
