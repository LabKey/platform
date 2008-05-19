/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
DROP VIEW study.SpecimenDetail;

ALTER TABLE study.Site
    ADD IsClinic Boolean;

ALTER TABLE study.Specimen
    ADD OriginatingLocationId INT,
    ADD CONSTRAINT FK_SpecimenOrigin_Site FOREIGN KEY (OriginatingLocationId) REFERENCES study.Site(RowId);

CREATE INDEX IX_Specimen_OriginatingLocationId ON study.Specimen(OriginatingLocationId);

CREATE VIEW study.SpecimenDetail AS
      SELECT SpecimenInfo.*,
        -- eliminate nulls in my left-outer-join fields:
        (CASE Locked WHEN True THEN True ELSE False END) AS LockedInRequest,
        (CASE AtRepository WHEN True THEN (CASE Locked WHEN True THEN False ELSE True END) ELSE False END) As Available
         FROM
            (
                SELECT
                    Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
                    VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
                    DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                    (CASE IsRepository WHEN True THEN True ELSE False END) AS AtRepository,
                    DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
                    OriginatingLocationId
                FROM
                    (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
                        Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
                        AND Event.ShipDate IS NULL
                        AND (Event.ShipBatchNumber IS NULL OR Event.ShipBatchNumber = 0)
                        AND (Event.ShipFlag IS NULL OR Event.ShipFlag = 0))
                    ) LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Event.LabId AND Site.Container = Event.Container)
        ) SpecimenInfo LEFT OUTER JOIN (
            SELECT *, True AS Locked
            FROM study.LockedSpecimens
        ) LockedSpecimens ON (SpecimenInfo.RowId = LockedSpecimens.RowId);

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN True THEN Volume ELSE 0 END) As AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, SubAdditiveDerivative, OriginatingLocationId,
        COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId;
