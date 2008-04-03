CREATE VIEW study.LockedSpecimens AS
    SELECT study.Specimen.RowId, study.Specimen.GlobalUniqueId, study.Specimen.Container
    FROM study.Specimen, study.SampleRequest, study.SampleRequestSpecimen, study.SampleRequestStatus
    WHERE
        study.SampleRequestSpecimen.SampleRequestId = study.SampleRequest.RowId AND
        study.SampleRequestSpecimen.SpecimenId = study.Specimen.RowId AND
        study.SampleRequest.StatusId = study.SampleRequestStatus.RowId AND
        study.SampleRequestStatus.SpecimensLocked = True
    GROUP BY study.Specimen.GlobalUniqueId, study.Specimen.RowId, study.Specimen.Container;

DROP VIEW study.SpecimenDetail;

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
                    AND Event.ShipDate IS NULL)
                ) LEFT OUTER JOIN study.Site AS Site ON
                    (Site.RowId = Event.LabId AND Site.Container = Event.Container)
    ) SpecimenInfo LEFT OUTER JOIN (
        SELECT *, True AS Locked
        FROM study.LockedSpecimens
    ) LockedSpecimens ON (SpecimenInfo.RowId = LockedSpecimens.RowId);

DROP VIEW study.SpecimenSummary;

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS Volume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue, ExpectedTimeUnit,
        SubAdditiveDerivative, SampleNumber, XSampleOrigin, ExternalLocation, RecordSource,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequest,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepository,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS Available
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative,SampleNumber,
        XSampleOrigin, ExternalLocation, RecordSource;