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
DROP VIEW study.SpecimenSummary;

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS Volume,
        SUM(CASE Available WHEN True THEN Volume ELSE 0 END) As AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, ExpectedTimeUnit,
        SubAdditiveDerivative, SampleNumber, XSampleOrigin, ExternalLocation, RecordSource,
        COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequest,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepository,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS Available
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative,SampleNumber,
        XSampleOrigin, ExternalLocation, RecordSource;