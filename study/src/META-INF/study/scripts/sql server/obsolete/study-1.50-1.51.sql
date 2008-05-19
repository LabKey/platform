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
CREATE VIEW study.LockedSpecimens AS
    SELECT study.Specimen.RowId, study.Specimen.GlobalUniqueId, study.Specimen.Container
    FROM study.Specimen, study.SampleRequest, study.SampleRequestSpecimen, study.SampleRequestStatus
    WHERE
        study.SampleRequestSpecimen.SampleRequestId = study.SampleRequest.RowId AND
        study.SampleRequestSpecimen.SpecimenId = study.Specimen.RowId AND
        study.SampleRequest.StatusId = study.SampleRequestStatus.RowId AND
        study.SampleRequestStatus.SpecimensLocked = 1
    GROUP BY study.Specimen.GlobalUniqueId, study.Specimen.RowId, study.Specimen.Container
GO

DROP VIEW study.SpecimenDetail
GO

CREATE VIEW study.SpecimenDetail AS
  SELECT SpecimenInfo.*,
    -- eliminate nulls in my left-outer-join fields:
    (CASE Locked WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequest,
    (CASE AtRepository WHEN 1 THEN (CASE Locked WHEN 1 THEN 0 ELSE 1 END) ELSE 0 END) As Available
     FROM
        (
            SELECT
                Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
                VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
                DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                (CASE IsRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepository,
                DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
                ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative, SpecimenCondition,
                SampleNumber, XSampleOrigin, ExternalLocation, UpdateTimestamp, RecordSource
            FROM
                (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
                    Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
                    AND Event.ShipDate IS NULL)
                ) LEFT OUTER JOIN study.Site AS Site ON
                    (Site.RowId = Event.LabId AND Site.Container = Event.Container)
    ) SpecimenInfo LEFT OUTER JOIN (
        SELECT *, 1 AS Locked
        FROM study.LockedSpecimens
    ) LockedSpecimens ON (SpecimenInfo.RowId = LockedSpecimens.RowId)
GO

DROP VIEW study.SpecimenSummary
GO

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS Volume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, ExpectedTimeUnit,
        SubAdditiveDerivative, SampleNumber, XSampleOrigin, ExternalLocation, RecordSource,
        SUM(LockedInRequest) AS LockedInRequest, SUM(AtRepository) AS AtRepository,
        SUM(Available) AS Available
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative,SampleNumber,
        XSampleOrigin, ExternalLocation, RecordSource
GO