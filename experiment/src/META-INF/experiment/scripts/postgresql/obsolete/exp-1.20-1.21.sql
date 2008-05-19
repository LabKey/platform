/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
ALTER TABLE exp.Data ADD COLUMN StudyParticipantId INT4 NULL;
ALTER TABLE exp.Data ADD COLUMN StudyDatasetId INT4 NULL;
ALTER TABLE exp.Data ADD COLUMN StudyVisitId INT4 NULL;

CREATE VIEW exp.StudyData AS
    SELECT * FROM exp.Data WHERE StudyDatasetId IS NOT NULL;

CREATE UNIQUE INDEX IDX_StudyData_ByVisit ON exp.Data (Container, StudyDatasetId, StudyVisitId, StudyParticipantId)
    WHERE StudyDatasetId IS NOT NULL;
CREATE UNIQUE INDEX IDX_StudyData_ByParticipant ON exp.Data (Container, StudyDatasetId, StudyParticipantId, StudyVisitId)
    WHERE StudyDatasetId IS NOT NULL;
