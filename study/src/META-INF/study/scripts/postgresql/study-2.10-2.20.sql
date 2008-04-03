ALTER TABLE study.SampleRequest ADD
    EntityId ENTITYID NULL;

ALTER TABLE study.SampleRequestRequirement
    ADD OwnerEntityId ENTITYID NULL,
    DROP CONSTRAINT FK_SampleRequestRequirement_SampleRequest;

CREATE INDEX IX_SampleRequest_EntityId ON study.SampleRequest(EntityId);
CREATE INDEX IX_SampleRequestRequirement_OwnerEntityId ON study.SampleRequestRequirement(OwnerEntityId);

DROP TABLE study.AssayRun;

DROP VIEW study.SpecimenSummary;
DROP VIEW study.SpecimenDetail;

ALTER TABLE study.Specimen
    ADD Requestable Boolean;

ALTER TABLE study.SpecimenEvent
    ADD freezer VARCHAR(200),
    ADD fr_level1 VARCHAR(200),
    ADD fr_level2 VARCHAR(200),
    ADD fr_container VARCHAR(200),
    ADD fr_position VARCHAR(200);

CREATE VIEW study.SpecimenDetail AS
      SELECT SpecimenInfo.*,
        -- eliminate nulls in my left-outer-join fields:
        (CASE Locked WHEN True THEN True ELSE False END) AS LockedInRequest,
        (
        CASE Requestable
        WHEN True THEN (
            CASE Locked
            WHEN True THEN False
            ELSE True
            END)
        WHEN False THEN False
        ELSE (
	    CASE AtRepository
            WHEN True THEN (
                CASE Locked
                WHEN True THEN False
                ELSE True
                END)
            ELSE False
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
                    (CASE IsRepository WHEN True THEN True ELSE False END) AS AtRepository,
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

DELETE FROM study.study WHERE NOT EXISTS
(SELECT Container FROM study.visit WHERE study.visit.container=study.study.Container)
AND NOT EXISTS (Select Container FROM study.dataset WHERE study.dataset.container=study.study.Container);
