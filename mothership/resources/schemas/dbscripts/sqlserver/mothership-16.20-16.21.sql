ALTER TABLE mothership.ServerSession ADD Distribution NVARCHAR(500) NULL;
ALTER TABLE mothership.ServerSession ADD JsonMetrics NVARCHAR(MAX) NULL;
ALTER TABLE mothership.ServerSession ADD UsageReportingLevel NVARCHAR(10) NULL;
ALTER TABLE mothership.ServerSession ADD ExceptionReportingLevel NVARCHAR(10) NULL;
