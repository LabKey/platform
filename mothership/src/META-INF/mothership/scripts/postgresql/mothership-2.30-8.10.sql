/* mothership-2.30-2.31.sql */

ALTER TABLE mothership.ServerSession ADD EnterprisePipelineEnabled BOOLEAN;

ALTER TABLE mothership.ServerSession ADD LDAPEnabled BOOLEAN;

/* mothership-2.31-2.32.sql */

ALTER TABLE mothership.ExceptionReport ADD SQLState VARCHAR(100);

DROP VIEW mothership.ExceptionReportSummary;

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
        r.PageflowName,
        r.PageflowAction,
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
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId;