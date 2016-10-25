/* mothership-16.20-16.21.sql */

ALTER TABLE mothership.ServerSession ADD COLUMN Distribution VARCHAR(500) NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN JsonMetrics VARCHAR NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN UsageReportingLevel VARCHAR(10) NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN ExceptionReportingLevel VARCHAR(10) NULL;