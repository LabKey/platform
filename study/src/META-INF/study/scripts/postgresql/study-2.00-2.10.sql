-- VISITMAP

ALTER TABLE study.visitmap RENAME COLUMN isrequired TO required;


-- VISIT

-- ALTER TABLE study.visit ADD COLUMN name VARCHAR(200);
--
-- UPDATE study.visit SET name=COALESCE(label,cast(rowid as VARCHAR(20)));
-- UPDATE study.visit SET name=rowid
-- WHERE 1 < (SELECT COUNT(*) FROM study.visit V where V.container=study.visit.container and V.name=study.visit.name)
--
-- ALTER TABLE study.visit ALTER name SET NOT NULL;
-- ALTER TABLE study.visit ADD CONSTRAINT UQ_VisitName UNIQUE (container, name);


-- DATASET

ALTER TABLE study.dataset ADD COLUMN name VARCHAR(200);

UPDATE study.dataset SET name=COALESCE(label,cast(datasetid as VARCHAR(20)));
UPDATE study.dataset SET name=datasetid
WHERE 1 < (SELECT COUNT(*) FROM study.dataset D where D.container=study.dataset.container and D.name=study.dataset.name);

ALTER TABLE study.dataset ALTER name SET NOT NULL;
ALTER TABLE study.dataset ADD CONSTRAINT UQ_DatasetName UNIQUE (container, name);

ALTER TABLE study.SampleRequestEvent ADD
    CONSTRAINT PK_SampleRequestEvent PRIMARY KEY (RowId);

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

ALTER TABLE study.StudyDesign ADD COLUMN StudyEntityId entityid;

ALTER TABLE study.Dataset ADD COLUMN Description TEXT NULL;

--ALTER TABLE study.StudyData DROP CONSTRAINT UQ_StudyData
--ALTER TABLE study.StudyData ADD CONSTRAINT UQ_StudyData UNIQUE CLUSTERED
--	(
--		Container,
--		DatasetId,
--		SequenceNum,
--		ParticipantId,
--		_key
--	)

ALTER TABLE study.StudyDesign
    ADD Active boolean NOT NULL DEFAULT FALSE;

UPDATE study.StudyDesign SET Active=true WHERE StudyEntityId IS NOT NULL;

ALTER TABLE study.StudyDesign
    DROP StudyEntityId,
    ADD SourceContainer ENTITYID;

UPDATE study.StudyDesign SET SourceContainer = Container;