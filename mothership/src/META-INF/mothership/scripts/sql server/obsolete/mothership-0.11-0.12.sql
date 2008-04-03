ALTER TABLE mothership.ExceptionReport ADD ReferrerURL VARCHAR(512)
GO

DROP VIEW mothership.ExceptionReportSummary
GO

CREATE VIEW mothership.ExceptionReportSummary AS
    SELECT
        r.ExceptionReportId,
        r.ExceptionStackTraceId,
        r.Created,
        r.ServerSessionId,
        r.URL,
        r.ReferrerURL,
        r.Username,
        r.Browser,
        ss.SVNRevision,
        ss.DatabaseProductName,
        ss.DatabaseProductVersion,
        ss.DatabaseDriverName,
        ss.DatabaseDriverVersion,
        ss.RuntimeOS,
        ss.ServerSessionGUID,
        st.StackTrace
    FROM
        mothership.ExceptionReport r, mothership.ServerSession ss, mothership.ExceptionStackTrace st
    WHERE
        r.ServerSessionId = ss.ServerSessionId
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId
GO

