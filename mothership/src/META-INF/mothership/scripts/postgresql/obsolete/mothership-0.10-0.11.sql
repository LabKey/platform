/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

SET search_path TO mothership, public;  -- include public to get ENTITYID, USERID

DROP VIEW ExceptionSummary;
DROP TABLE ExceptionReport;
DROP TABLE ServerSession;
DROP TABLE ServerInstallation;
DROP TABLE ExceptionStackTrace;

CREATE TABLE ExceptionStackTrace
	(
	ExceptionStackTraceId SERIAL NOT NULL,
	Container ENTITYID NOT NULL,
	StackTrace TEXT NOT NULL,
	StackTraceHash VARCHAR(50) NOT NULL,
	AssignedTo USERID,
	BugNumber INT,

	CONSTRAINT PK_ExceptionStackTrace PRIMARY KEY (ExceptionStackTraceId),
	CONSTRAINT UQ_ExceptionStackTraceId_StackTraceHashContainer UNIQUE (StackTraceHash, Container),
	CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(UserId)
	);


CREATE TABLE ServerInstallation
	(
	ServerInstallationId SERIAL NOT NULL,
	ServerInstallationGUID ENTITYID NOT NULL,
	Note VARCHAR(150),
	Container ENTITYID NOT NULL,
	SystemDescription VARCHAR(200),
	LogoLink VARCHAR(200),
	OrganizationName VARCHAR(200),
	SystemShortName VARCHAR(200),
	ServerIP VARCHAR(20),

	CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
	CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
	CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	);

CREATE TABLE ServerSession
	(
	ServerSessionId SERIAL NOT NULL,
	ServerInstallationId INT,
	ServerSessionGUID ENTITYID NOT NULL,
	EarliestKnownTime TIMESTAMP NOT NULL,
	LastKnownTime TIMESTAMP NOT NULL,
	Container ENTITYID NOT NULL,
	DatabaseProductName VARCHAR(200),
	DatabaseProductVersion VARCHAR(200),
	DatabaseDriverName VARCHAR(200),
	DatabaseDriverVersion VARCHAR(200),
	RuntimeOS VARCHAR(100),
	SVNRevision INT,

	CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
	CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
	CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES ServerInstallation(ServerInstallationId),
	CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	);

CREATE TABLE ExceptionReport
	(
	ExceptionReportId SERIAL NOT NULL,
	ExceptionStackTraceId INT,
	Created TIMESTAMP DEFAULT now(),
	URL VARCHAR(512),
	ServerSessionId INT NOT NULL,
	Username VARCHAR(50),
	Browser VARCHAR(100),

	CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
	CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES ExceptionStackTrace(ExceptionStackTraceId),
	CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES ServerSession(ServerSessionId)
	);


CREATE VIEW ExceptionSummary AS
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
    FROM ExceptionStackTrace st INNER JOIN (
        SELECT
            a.ExceptionStackTraceId,
            MAX(ss.SVNRevision) AS MaxSVNRevision,
            MIN(ss.SVNRevision) AS MinSVNRevision,
            COUNT(r.ExceptionReportId) AS Instances,
            MAX(r.Created) AS LastReport,
            MIN(r.Created) AS FirstReport
        FROM
            ExceptionStackTrace a, ExceptionReport r, ServerSession ss
        WHERE
            a.ExceptionStackTraceId = r.ExceptionStackTraceId
            AND ss.ServerSessionId = r.ServerSessionId
        GROUP BY
            a.ExceptionStackTraceId
    ) q
    ON q.ExceptionStackTraceId = st.ExceptionStackTraceId;

CREATE VIEW ExceptionReportSummary AS
    SELECT
        r.ExceptionReportId,
        r.ExceptionStackTraceId,
        r.Created,
        r.ServerSessionId,
        r.URL,
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
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId

