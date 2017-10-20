ALTER TABLE mothership.exceptionreport
    ADD COLUMN ErrorCode VARCHAR(6);

CREATE INDEX IX_ExceptionReport_ErrorCode ON mothership.exceptionreport (ErrorCode);

