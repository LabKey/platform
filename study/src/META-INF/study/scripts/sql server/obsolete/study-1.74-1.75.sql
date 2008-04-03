ALTER TABLE study.Report DROP CONSTRAINT PK_Report
go
ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId)
go
