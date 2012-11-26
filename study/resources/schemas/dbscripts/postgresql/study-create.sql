/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

CREATE VIEW study.VialCounts AS
    SELECT Container, SpecimenHash,
    SUM(Volume) AS TotalVolume,
    SUM(CASE Available WHEN True THEN Volume ELSE 0 END) AS AvailableVolume,
    COUNT(GlobalUniqueId) AS VialCount,
    SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
    SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
    SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount,
    (
        COUNT(GlobalUniqueId) - SUM(
            CASE
                (CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) |  -- Null is considered false for LockedInRequest
                (CASE Requestable WHEN False THEN 1 ELSE 0 END)       -- Null is considered true for Requestable
            WHEN 1 THEN 1 ELSE 0 END)
    ) AS ExpectedAvailableCount
    FROM study.Vial
    GROUP BY Container, SpecimenHash;

CREATE VIEW study.SpecimenSummary AS SELECT * FROM study.Specimen;


CREATE VIEW study.SpecimenDetail AS
    SELECT Vial.*,
        Specimen.Ptid,
        Specimen.ParticipantSequenceNum,
        Specimen.TotalVolume,
        Specimen.AvailableVolume,
        Specimen.VisitDescription,
        Specimen.VisitValue,
        Specimen.VolumeUnits,
        Specimen.PrimaryTypeId,
        Specimen.AdditiveTypeId,
        Specimen.DerivativeTypeId,
        Specimen.DerivativeTypeId2,
        Specimen.SubAdditiveDerivative,
        Specimen.DrawTimestamp,
        Specimen.SalReceiptDate,
        Specimen.ClassId,
        Specimen.ProtocolNumber,
        Specimen.OriginatingLocationId,
        Specimen.VialCount,
        Specimen.LockedInRequestCount,
        Specimen.AtRepositoryCount,
        Specimen.AvailableCount,
        Specimen.ExpectedAvailableCount
    FROM study.Vial AS Vial
        INNER JOIN study.Specimen AS Specimen
        ON Vial.SpecimenId = Specimen.RowId;


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