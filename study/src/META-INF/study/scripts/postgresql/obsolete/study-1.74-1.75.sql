ALTER TABLE study.Report DROP CONSTRAINT PK_Report;

ALTER TABLE study.Report ADD CONSTRAINT PK_Report PRIMARY KEY (ContainerId, ReportId);
