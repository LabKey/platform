DROP VIEW study.SpecimenSummary;
DROP VIEW study.SpecimenDetail;

ALTER TABLE study.specimen
    ADD column CurrentLocation INT,
    ADD CONSTRAINT FK_CurrentLocation_Site FOREIGN KEY (CurrentLocation) references study.Site(RowId);

CREATE INDEX IX_Specimen_CurrentLocation ON study.Specimen(CurrentLocation);
CREATE INDEX IX_Specimen_VisitValue ON study.Specimen(VisitValue);
CREATE INDEX IX_Visit_SequenceNumMin ON study.Visit(SequenceNumMin);
CREATE INDEX IX_Visit_ContainerSeqNum ON study.Visit(Container, SequenceNumMin);

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
                SELECT Specimen.*, (CASE IsRepository WHEN True THEN True ELSE False END) AS AtRepository,
                     Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode
	        from study.Specimen as Specimen
                LEFT OUTER JOIN study.Site AS Site ON
                        (Site.RowId = Specimen.CurrentLocation)
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