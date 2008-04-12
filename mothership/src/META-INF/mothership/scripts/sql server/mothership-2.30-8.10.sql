/* mothership-2.30-2.31.sql */

ALTER TABLE mothership.ServerSession ADD EnterprisePipelineEnabled BIT
GO

ALTER TABLE mothership.ServerSession ADD LDAPEnabled BIT
GO

/* mothership-2.31-2.32.sql */

ALTER TABLE mothership.ExceptionReport ADD SQLState VARCHAR(100)
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
        r.PageflowName,
        r.PageflowAction,
        r.Username,
        r.Browser,
        r.SQLState,
        ss.SVNRevision,
        ss.DatabaseProductName,
        ss.DatabaseProductVersion,
        ss.DatabaseDriverName,
        ss.DatabaseDriverVersion,
        ss.RuntimeOS,
        ss.JavaVersion,
        ss.ServerSessionGUID,
        st.StackTrace
    FROM
        mothership.ExceptionReport r, mothership.ServerSession ss, mothership.ExceptionStackTrace st
    WHERE
        r.ServerSessionId = ss.ServerSessionId
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId
GO