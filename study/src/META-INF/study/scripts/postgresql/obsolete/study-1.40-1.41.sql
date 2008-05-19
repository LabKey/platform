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
ALTER TABLE study.SampleRequestStatus
    ADD FinalState Boolean NOT NULL DEFAULT '0',
    ADD SpecimensLocked Boolean NOT NULL DEFAULT '1';

CREATE VIEW study.SpecimenDetail AS
    SELECT
        Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
        VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
        DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
        IsRepository, DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative, SpecimenCondition,
        SampleNumber, XSampleOrigin, ExternalLocation, UpdateTimestamp, RecordSource
    FROM
        (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
            Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
            AND Event.ShipDate IS NULL)
        ) LEFT OUTER JOIN study.Site AS Site ON
            (Site.RowId = Event.LabId AND Site.Container = Event.Container);

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, SUM(Volume) As Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue,
        ExpectedTimeUnit, GroupProtocol, SubAdditiveDerivative,
        SampleNumber, XSampleOrigin, ExternalLocation, RecordSource
    FROM study.Specimen
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue,
        ExpectedTimeUnit, GroupProtocol, SubAdditiveDerivative,
        SampleNumber, XSampleOrigin, ExternalLocation, RecordSource;