ALTER TABLE study.SampleRequestStatus ADD
    FinalState BIT NOT NULL DEFAULT 0,
    SpecimensLocked BIT NOT NULL DEFAULT 1
go

ALTER TABLE study.Specimen
    ALTER COLUMN Ptid NVARCHAR(32)
GO

CREATE VIEW study.SpecimenDetail AS
    SELECT
        Specimen.Container, Specimen.RowId, SpecimenNumber, GlobalUniqueId, Ptid,
        VisitDescription, VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId,
        DerivativeTypeId, Site.Label AS SiteName,Site.LdmsLabCode AS SiteLdmsCode,
        IsRepository, DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId,
        ExpectedTimeValue, ExpectedTimeUnit, SubAdditiveDerivative, SpecimenCondition,
        SampleNumber, XSampleOrigin, ExternalLocation, UpdateTimestamp, RecordSource
    FROM
        (study.Specimen AS Specimen LEFT OUTER JOIN study.SpecimenEvent AS Event ON (
            Specimen.RowId = Event.SpecimenId AND Specimen.Container = Event.Container
            AND Event.ShipDate IS NULL)
        ) LEFT OUTER JOIN study.Site AS Site ON
            (Site.RowId = Event.LabId AND Site.Container = Event.Container)
go

CREATE VIEW study.SpecimenSummary AS
    SELECT Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, SUM(Volume) As Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue,
        ExpectedTimeUnit, GroupProtocol, SubAdditiveDerivative,
        SampleNumber, XSampleOrigin, ExternalLocation, RecordSource
    FROM study.Specimen
    GROUP BY Container, SpecimenNumber, Ptid, VisitDescription,
        VisitValue, Volume, VolumeUnits, PrimaryTypeId, AdditiveTypeId, DerivativeTypeId,
        DrawTimestamp, SalReceiptDate, ClassId, ProtocolNumber, OtherSpecimenId, ExpectedTimeValue,
        ExpectedTimeUnit, GroupProtocol, SubAdditiveDerivative,
        SampleNumber, XSampleOrigin, ExternalLocation, RecordSource
go

ALTER TABLE study.Participant ADD
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,
	CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
	CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
go

ALTER TABLE study.SpecimenEvent
    DROP CONSTRAINT UQ_Specimens_ScharpId
go
