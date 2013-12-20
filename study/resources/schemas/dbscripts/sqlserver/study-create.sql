/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
CREATE VIEW study.LockedSpecimens AS
  SELECT map.SpecimenGlobalUniqueId AS GlobalUniqueId, map.Container FROM study.SampleRequest AS request
      JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = 1
      JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = 0
GO

CREATE VIEW study.ParticipantGroupCohortUnion AS
  SELECT Container,
    ParticipantId,
    GroupId,
    null AS CohortId,
    CAST(GroupId AS NVARCHAR) + '-participantGroup' as UniqueId
  FROM study.ParticipantGroupMap
  UNION
  SELECT Container,
    ParticipantId,
    null AS GroupId,
    Currentcohortid AS CohortId,
    CAST(CurrentCohortId AS NVARCHAR) + '-cohort' as UniqueId
  FROM study.Participant;
