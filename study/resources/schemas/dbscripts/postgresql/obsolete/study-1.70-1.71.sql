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
ALTER TABLE study.SampleRequestSpecimen
ADD COLUMN SpecimenGlobalUniqueId VARCHAR(100);

UPDATE study.SampleRequestSpecimen SET SpecimenGlobalUniqueId =
    (SELECT GlobalUniqueId FROM study.Specimen WHERE RowId = SpecimenId);

DROP VIEW study.SpecimenSummary;
DROP VIEW study.SpecimenDetail;
DROP VIEW study.LockedSpecimens;

ALTER TABLE study.Specimen
    DROP COLUMN SpecimenCondition,
    DROP COLUMN SampleNumber,
    DROP COLUMN XSampleOrigin,
    DROP COLUMN ExternalLocation,
    DROP COLUMN UpdateTimestamp,
    DROP COLUMN OtherSpecimenId,
    DROP COLUMN ExpectedTimeUnit,
    DROP COLUMN RecordSource,
    DROP COLUMN GroupProtocol,
    DROP COLUMN ExpectedTimeValue;

ALTER TABLE study.SpecimenEvent
    ADD COLUMN SpecimenCondition VARCHAR(3),
    ADD COLUMN SampleNumber INT,
    ADD COLUMN XSampleOrigin VARCHAR(20),
    ADD COLUMN ExternalLocation VARCHAR(20),
    ADD COLUMN UpdateTimestamp TIMESTAMP,
    ADD COLUMN OtherSpecimenId VARCHAR(20),
    ADD COLUMN ExpectedTimeUnit VARCHAR(15),
    ADD COLUMN ExpectedTimeValue FLOAT,
    ADD COLUMN GroupProtocol INT,
    ADD COLUMN RecordSource VARCHAR(10);

CREATE VIEW study.LockedSpecimens AS
    SELECT study.Specimen.RowId, study.Specimen.GlobalUniqueId, study.Specimen.Container
    FROM study.Specimen, study.SampleRequest, study.SampleRequestSpecimen, study.SampleRequestStatus
    WHERE
        study.SampleRequestSpecimen.SampleRequestId = study.SampleRequest.RowId AND
        study.SampleRequestSpecimen.SpecimenGlobalUniqueId = study.Specimen.GlobalUniqueId AND
        study.SampleRequestSpecimen.Container = study.Specimen.Container AND
        study.SampleRequest.StatusId = study.SampleRequestStatus.RowId AND
        study.SampleRequestStatus.SpecimensLocked = True
    GROUP BY study.Specimen.GlobalUniqueId, study.Specimen.RowId, study.Specimen.Container;

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
                    DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
                    ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative, SpecimenCondition,
                    SampleNumber, XSampleOrigin, ExternalLocation, UpdateTimestamp, RecordSource
                FROM
                    (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
                        Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
                        AND Event.ShipDate IS NULL AND Event.ShipBatchNumber IS NULL)
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
        ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, ExpectedTimeUnit,
        SubAdditiveDerivative, SampleNumber, XSampleOrigin, ExternalLocation, RecordSource,
        COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative,SampleNumber,
        XSampleOrigin, ExternalLocation, RecordSource;

-- fix up study.SampleRequestSpecimen
ALTER TABLE study.SampleRequestSpecimen DROP CONSTRAINT fk_SampleRequestSpecimen_specimen;
ALTER TABLE study.SampleRequestSpecimen DROP COLUMN SpecimenId;

ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Additives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_Derivatives;
ALTER TABLE study.Specimen DROP CONSTRAINT FK_Specimens_PrimaryTypes;

ALTER TABLE study.SpecimenAdditive DROP CONSTRAINT UQ_Additives;
UPDATE study.SpecimenAdditive SET ScharpId = RowId;
CREATE INDEX IX_SpecimenAdditive_ScharpId ON study.SpecimenAdditive(ScharpId);
ALTER TABLE study.SpecimenAdditive ADD CONSTRAINT UQ_Additives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenDerivative DROP CONSTRAINT UQ_Derivatives;
UPDATE study.SpecimenDerivative SET ScharpId = RowId;
CREATE INDEX IX_SpecimenDerivative_ScharpId ON study.SpecimenDerivative(ScharpId);
ALTER TABLE study.SpecimenDerivative ADD CONSTRAINT UQ_Derivatives UNIQUE (ScharpId, Container);

ALTER TABLE study.SpecimenPrimaryType DROP CONSTRAINT UQ_PrimaryTypes;
UPDATE study.SpecimenPrimaryType SET ScharpId = RowId;
CREATE INDEX IX_SpecimenPrimaryType_ScharpId ON study.SpecimenPrimaryType(ScharpId);
ALTER TABLE study.SpecimenPrimaryType ADD CONSTRAINT UQ_PrimaryTypes UNIQUE (ScharpId, Container);

CREATE INDEX IX_SpecimenEvent_SpecimenId ON study.SpecimenEvent(SpecimenId);
CREATE INDEX IX_Specimen_PrimaryTypeId ON study.Specimen(PrimaryTypeId);
CREATE INDEX IX_Specimen_DerivativeTypeId ON study.Specimen(DerivativeTypeId);
CREATE INDEX IX_Specimen_AdditiveTypeId ON study.Specimen(AdditiveTypeId);
