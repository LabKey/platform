/*
 * Copyright (c) 2008 LabKey Corporation
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
    SELECT study.Specimen.RowId, study.Specimen.GlobalUniqueId, study.Specimen.Container
    FROM study.Specimen, study.SampleRequest, study.SampleRequestSpecimen, study.SampleRequestStatus
    WHERE
        study.SampleRequestSpecimen.SampleRequestId = study.SampleRequest.RowId AND
        study.SampleRequestSpecimen.SpecimenGlobalUniqueId = study.Specimen.GlobalUniqueId AND
        study.SampleRequestSpecimen.Container = study.Specimen.Container AND
        study.SampleRequest.StatusId = study.SampleRequestStatus.RowId AND
        study.SampleRequestStatus.SpecimensLocked = 1
    GROUP BY study.Specimen.GlobalUniqueId, study.Specimen.RowId, study.Specimen.Container
GO

CREATE VIEW study.SpecimenDetail AS
      SELECT SpecimenInfo.*,
        -- eliminate nulls in my left-outer-join fields:
        (
        CASE Requestable
        WHEN 1 THEN (
            CASE LockedInRequest
            WHEN 1 THEN 0
            ELSE 1
            END)
        WHEN 0 THEN 0
        ELSE (
	    CASE AtRepository
            WHEN 1 THEN (
                CASE LockedInRequest
                WHEN 1 THEN 0
                ELSE 1
                END)
            ELSE 0
            END)
	    END
	    ) AS Available
         FROM
            (
                SELECT Specimen.*, (CASE IsRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepository,
                     Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                    (CASE LockedSpecimens.Locked WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequest
    	        FROM study.Specimen AS Specimen
                LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Specimen.CurrentLocation)
                LEFT OUTER JOIN (SELECT *, 1 AS Locked FROM study.LockedSpecimens) LockedSpecimens ON
                    LockedSpecimens.GlobalUniqueId = Specimen.GlobalUniqueId AND
                    LockedSpecimens.Container = Specimen.Container
        ) SpecimenInfo
GO

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenHash, Ptid, VisitDescription, VisitValue, SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN 1 THEN Volume ELSE 0 END) AS AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, SubAdditiveDerivative, OriginatingLocationId,
        COUNT(GlobalUniqueId) AS VialCount,
        SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN 1 THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenHash, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId
GO
