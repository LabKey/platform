/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
CREATE VIEW mothership.ExceptionSummary AS
    SELECT
        st.ExceptionStackTraceId,
        st.StackTrace,
        q.MaxSVNRevision,
        q.MinSVNRevision,
        q.Instances,
        q.LastReport,
        q.FirstReport,
        st.Container,
        st.BugNumber,
        st.AssignedTo
    FROM mothership.ExceptionStackTrace st INNER JOIN (
        SELECT
            a.ExceptionStackTraceId,
            MAX(ss.SVNRevision) AS MaxSVNRevision,
            MIN(ss.SVNRevision) AS MinSVNRevision,
            COUNT(r.ExceptionReportId) AS Instances,
            MAX(r.Created) AS LastReport,
            MIN(r.Created) AS FirstReport
        FROM
            mothership.ExceptionStackTrace a, mothership.ExceptionReport r, mothership.ServerSession ss
        WHERE
            a.ExceptionStackTraceId = r.ExceptionStackTraceId
            AND ss.ServerSessionId = r.ServerSessionId
        GROUP BY
            a.ExceptionStackTraceId
    ) q
    ON q.ExceptionStackTraceId = st.ExceptionStackTraceId;

CREATE VIEW mothership.ServerInstallationWithSession AS
    SELECT
        i.ServerInstallationId,
        i.ServerInstallationGUID,
        i.Note,
        i.Container,
        i.SystemDescription,
        i.LogoLink,
        i.OrganizationName,
        i.SystemShortName,
        i.ServerIP,
        i.ServerHostName,
        s.LastKnownTime
    FROM
        mothership.ServerInstallation i,
        ( SELECT MAX(lastknowntime) AS LastKnownTime, ServerInstallationId
            FROM mothership.ServerSession
            GROUP BY ServerInstallationId ) s
    WHERE
        i.ServerInstallationId = s.ServerInstallationId;

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
