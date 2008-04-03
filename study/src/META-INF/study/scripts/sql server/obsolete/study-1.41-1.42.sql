ALTER TABLE study.Participant ADD
    EnrollmentSiteId INT NULL,
    CurrentSiteId INT NULL,
	CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
	CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
go
