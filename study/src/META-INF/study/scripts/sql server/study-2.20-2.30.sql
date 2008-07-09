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
  go

UPDATE study.Study SET DateBased=0 where DateBased is NULL
go

ALTER TABLE study.ParticipantVisit ADD
    Day INTEGER
  go

ALTER TABLE study.Participant ADD
    StartDate DATETIME
go

ALTER TABLE study.Dataset
ADD DemographicData BIT
go

UPDATE study.Dataset SET DemographicData=0 where DemographicData IS NULL
go

ALTER TABLE study.Dataset
ADD CONSTRAINT DF_DemographicData_False
DEFAULT 0 FOR DemographicData
go

ALTER TABLE study.Study ADD StudySecurity BIT DEFAULT 0
Go

UPDATE study.Study SET StudySecurity=1 where StudySecurity is NULL
Go
