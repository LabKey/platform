/* mothership-16.30-16.31.sql */

CREATE NONCLUSTERED INDEX IX_ExceptionReport_Created ON mothership.exceptionreport (created DESC);
ALTER TABLE mothership.serverinstallation ADD IgnoreExceptions BIT;

ALTER TABLE mothership.exceptionstacktrace
    ADD LastReport DATETIME,
    FirstReport DATETIME,
    Instances INT;
GO

UPDATE mothership.ExceptionStackTrace
SET instances = x.Instances, LastReport = x.LastReport, FirstReport= x.FirstReport
FROM mothership.ExceptionStackTrace est JOIN
(SELECT exceptionStackTraceId, count(exceptionReportId) Instances, max(created) LastReport, min(created) FirstReport FROM mothership.ExceptionReport GROUP BY ExceptionStackTraceId) x
ON est.ExceptionStackTraceId = x.ExceptionStackTraceId;