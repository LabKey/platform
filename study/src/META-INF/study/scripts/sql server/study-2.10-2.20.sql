ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL
GO

ALTER TABLE study.SampleRequestRequirement
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;
GO

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);
GO

DROP TABLE study.AssayRun
GO

DROP VIEW study.SpecimenSummary
DROP VIEW study.SpecimenDetail
GO

ALTER TABLE study.Specimen
    ADD Requestable BIT

ALTER TABLE study.SpecimenEvent ADD
    freezer NVARCHAR(200),
    fr_level1 NVARCHAR(200),
    fr_level2 NVARCHAR(200),
    fr_container NVARCHAR(200),
    fr_position NVARCHAR(200)
GO

CREATE VIEW study.SpecimenDetail AS
      SELECT SpecimenInfo.*,
        -- eliminate nulls in my left-outer-join fields:
        (CASE Locked WHEN 1 THEN 1 ELSE 0 END) AS LockedInRequest,
        (
        CASE Requestable
        WHEN 1 THEN (
            CASE Locked
            WHEN 1 THEN 0
            ELSE 1
            END)
        WHEN 0 THEN 0
        ELSE (
	    CASE AtRepository
            WHEN 1 THEN (
                CASE Locked
                WHEN 1 THEN 0
                ELSE 1
                END)
            ELSE 0
            END)
	    END
	    ) As Available
         FROM
            (
                SELECT
                    Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
                    VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
                    DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
                    fr_container, fr_level1, fr_level2, fr_position, freezer,
                    (CASE IsRepository WHEN 1 THEN 1 ELSE 0 END) AS AtRepository,
                    DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, SubAdditiveDerivative,
                    OriginatingLocationId, Requestable
                FROM
                    (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
                        Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
                        AND Event.ShipDate IS NULL
                        AND (Event.ShipBatchNumber IS NULL OR Event.ShipBatchNumber = 0)
                        AND (Event.ShipFlag IS NULL OR Event.ShipFlag = 0))
                    ) LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Event.LabId AND Site.Container = Event.Container)
        ) SpecimenInfo LEFT OUTER JOIN (
            SELECT *, 1 AS Locked
            FROM study.LockedSpecimens
        ) LockedSpecimens ON (SpecimenInfo.RowId = LockedSpecimens.RowId)
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

DELETE FROM study.study WHERE NOT EXISTS
(SELECT Container FROM study.visit WHERE study.visit.container=study.study.Container)
AND NOT EXISTS (Select Container FROM study.dataset WHERE study.dataset.container=study.study.Container)
go