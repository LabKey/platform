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
if exists (select * from dbo.sysobjects where id = object_id(N'exp.StudyData') and OBJECTPROPERTY(id, N'IsView') = 1)
DROP VIEW exp.StudyData
go

SET ANSI_NULLS ON
SET QUOTED_IDENTIFIER ON
go

CREATE VIEW exp.StudyData
WITH SCHEMABINDING
AS
SELECT RowId, Container, StudyDatasetId, StudyParticipantId, StudyVisitId, LSID
FROM exp.Data
WHERE StudyDatasetId IS NOT NULL
go

CREATE UNIQUE CLUSTERED INDEX IDX_StudyData_ByVisit ON exp.StudyData (Container, StudyDatasetId, StudyVisitId, StudyParticipantId);
CREATE INDEX IDX_StudyData_ByParticipant ON exp.StudyData (Container, StudyDatasetId, StudyParticipantId, StudyVisitId);
go



