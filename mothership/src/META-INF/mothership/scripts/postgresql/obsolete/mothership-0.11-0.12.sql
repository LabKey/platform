SET search_path TO mothership, public;  -- include public to get ENTITYID, USERID

ALTER TABLE ExceptionReport ADD COLUMN ReferrerURL VARCHAR(512);

DROP VIEW ExceptionReportSummary;

CREATE VIEW ExceptionReportSummary AS
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
        ExceptionReport r, ServerSession ss, ExceptionStackTrace st
    WHERE
        r.ServerSessionId = ss.ServerSessionId
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId;


