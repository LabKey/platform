DROP VIEW study.SpecimenSummary
GO
DROP VIEW study.SpecimenDetail
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
	    ) As Available
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
    SELECT Container, SpecimenNumber, Ptid, VisitDescription, VisitValue, SUM(Volume) AS TotalVolume,
        SUM(CASE Available WHEN 1 THEN Volume ELSE 0 END) As AvailableVolume,
        VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId, DrawTimestamp, SalReceiptDate,
        ClassId, ProtocolNumber, SubAdditiveDerivative, OriginatingLocationId,
        COUNT(GlobalUniqueId) As VialCount,
        SUM(CASE LockedInRequest WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequestCount,
        SUM(CASE AtRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepositoryCount,
        SUM(CASE Available WHEN 1 THEN 1 ELSE 0 END) AS AvailableCount
    FROM study.SpecimenDetail
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
        OriginatingLocationId
GO