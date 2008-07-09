-- DROP current views.
EXEC core.fn_dropifexists 'ExceptionReportSummary', 'mothership', 'VIEW', NULL
EXEC core.fn_dropifexists 'ServerInstallationWithSession', 'mothership', 'VIEW', NULL
EXEC core.fn_dropifexists 'ExceptionSummary', 'mothership', 'VIEW', NULL
GO
