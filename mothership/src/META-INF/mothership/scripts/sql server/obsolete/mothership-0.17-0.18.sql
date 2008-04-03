update mothership.serverinstallation set serverhostname=serverip where serverhostname is null
GO

delete from mothership.serverinstallation where serverinstallationid not in (select serverinstallationid from mothership.serversession)
GO

CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(serverinstallationid)
GO
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(exceptionstacktraceid)
GO
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(serversessionid)
GO

CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(container)
GO
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container)
GO
