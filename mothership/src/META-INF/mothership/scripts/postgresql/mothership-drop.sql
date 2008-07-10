-- DROP current views.
SELECT core.fn_dropifexists('ExceptionReportSummary', 'mothership', 'VIEW', NULL);
SELECT core.fn_dropifexists('ServerInstallationWithSession', 'mothership', 'VIEW', NULL);
SELECT core.fn_dropifexists('ExceptionSummary', 'mothership', 'VIEW', NULL);
