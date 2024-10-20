/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
        JOIN study.SampleRequestStatus AS status ON request.StatusId = status.RowId AND status.SpecimensLocked = True
        JOIN study.SampleRequestSpecimen AS map ON request.rowid = map.SampleRequestId AND map.Orphaned = False;

CREATE VIEW study.ParticipantGroupCohortUnion AS
    SELECT Container,
        ParticipantId,
        GroupId,
        null AS CohortId,
        GroupId || '-participantGroup' as UniqueId
    FROM study.ParticipantGroupMap
    UNION
    SELECT Container,
        ParticipantId,
        null AS GroupId,
        Currentcohortid AS CohortId,
        CurrentCohortId || '-cohort' as UniqueId
    FROM study.Participant;
