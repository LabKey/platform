/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

ALTER TABLE study.Visit ADD COLUMN ChronologicalOrder INTEGER NOT NULL DEFAULT 0;

ALTER TABLE study.ParticipantVisit
    ADD COLUMN CohortID INT NULL,
    ADD CONSTRAINT FK_ParticipantVisit_Cohort FOREIGN KEY (CohortID) REFERENCES study.Cohort (RowId);

ALTER TABLE study.Participant DROP CONSTRAINT FK_Participant_Cohort;
ALTER TABLE study.Participant RENAME COLUMN CohortId TO CurrentCohortId;
ALTER TABLE study.Participant ADD COLUMN InitialCohortId INTEGER;
UPDATE study.Participant SET InitialCohortId = CurrentCohortId;
CREATE INDEX IX_Participant_InitialCohort ON study.Participant(InitialCohortId);
CREATE INDEX IX_Participant_CurrentCohort ON study.Participant(CurrentCohortId);

ALTER TABLE study.Study
    ADD COLUMN AdvancedCohorts BOOLEAN NOT NULL DEFAULT FALSE;