ALTER TABLE mothership.exceptionreport
    ADD ErrorCode NVARCHAR(6);
GO

CREATE NONCLUSTERED INDEX IX_ExceptionReport_ErrorCode ON mothership.exceptionreport (ErrorCode);

