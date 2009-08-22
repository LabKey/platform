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
ALTER TABLE study.Study ADD
    DateBased BIT DEFAULT 0,
    StartDate DATETIME
  GO

UPDATE study.Study SET DateBased=0 WHERE DateBased is NULL
GO

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
  GO

ALTER TABLE study.Participant ADD
    StartDate DATETIME
GO

ALTER TABLE study.Dataset
ADD DemographicData BIT
GO

UPDATE study.Dataset SET DemographicData=0 WHERE DemographicData IS NULL
GO

ALTER TABLE study.Dataset
ADD CONSTRAINT DF_DemographicData_False
DEFAULT 0 FOR DemographicData
GO

ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
GO

UPDATE study.Study SET StudySecurity=1 WHERE StudySecurity is NULL
GO
