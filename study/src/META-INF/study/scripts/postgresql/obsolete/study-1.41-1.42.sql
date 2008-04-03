ALTER TABLE study.Participant
    ADD COLUMN EnrollmentSiteId INT NULL,
    ADD COLUMN CurrentSiteId INT NULL,
	ADD CONSTRAINT FK_EnrollmentSiteId_Site FOREIGN KEY (EnrollmentSiteId) REFERENCES study.Site (RowId),
	ADD CONSTRAINT FK_CurrentSiteId_Site FOREIGN KEY (CurrentSiteId) REFERENCES study.Site (RowId)
;
