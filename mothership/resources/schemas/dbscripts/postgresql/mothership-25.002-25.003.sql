ALTER TABLE mothership.ExceptionStackTrace ADD COLUMN GitHubIssue INT;

ALTER TABLE mothership.ExceptionStackTrace RENAME COLUMN BugNumber TO LabKeyIssue;