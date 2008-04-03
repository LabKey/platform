/*
 * Fix for https://cpas.fhcrc.org/Issues/home/issues/details.view?issueId=3111
 */

DROP VIEW study.SpecimenSummary;
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
                    DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative
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
        ClassId, ProtocolNumber, SubAdditiveDerivative, COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN True THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN True THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN True THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative;
