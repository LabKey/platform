/* mothership-16.30-16.31.sql */

CREATE INDEX IX_ExceptionReport_Created ON mothership.exceptionreport(created DESC);
ALTER TABLE mothership.serverinstallation ADD COLUMN IgnoreExceptions BOOLEAN;

ALTER TABLE mothership.exceptionstacktrace
  ADD COLUMN LastReport TIMESTAMP,
  ADD COLUMN FirstReport TIMESTAMP,
  ADD COLUMN Instances INT;

UPDATE mothership.ExceptionStackTrace est
SET instances = x.Instances, LastReport = x.LastReport, FirstReport= x.FirstReport
FROM (SELECT exceptionStackTraceId, count(exceptionReportId) Instances, max(created) LastReport, min(created) FirstReport FROM mothership.ExceptionReport GROUP BY ExceptionStackTraceId) x
WHERE est.ExceptionStackTraceId = x.ExceptionStackTraceId;