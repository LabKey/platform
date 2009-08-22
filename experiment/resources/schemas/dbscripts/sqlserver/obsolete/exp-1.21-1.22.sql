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
-- ParticipantId --> VARCHAR()

DROP VIEW exp.StudyData
go

ALTER TABLE exp.Data ALTER COLUMN StudyParticipantId VARCHAR(16) NULL
go

SET ARITHABORT ON
set QUOTED_IDENTIFIER ON
go

CREATE VIEW "exp"."StudyData" WITH SCHEMABINDING AS
    SELECT "Container", "StudyDatasetId", "StudyParticipantId", "StudyVisitId", "LSID" FROM "exp"."Data" WHERE "StudyDatasetId" IS NOT NULL
go

CREATE UNIQUE CLUSTERED INDEX IDX_StudyData_ByVisit ON exp.StudyData (Container, StudyDatasetId, StudyVisitId, StudyParticipantId);
CREATE INDEX IDX_StudyData_ByParticipant ON exp.StudyData (Container, StudyDatasetId, StudyParticipantId, StudyVisitId);
go


-- index string/float properties

CREATE INDEX IDX_ObjectProperty_FloatValue ON exp.ObjectProperty (PropertyId, FloatValue)
CREATE INDEX IDX_ObjectProperty_StringValue ON exp.ObjectProperty (PropertyId, StringValue)
go

