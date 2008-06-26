/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

EXEC sp_addapprole 'mothership', 'password'
GO

CREATE TABLE mothership.ExceptionStackTrace
	(
	ExceptionStackTraceId INT IDENTITY(1,1) NOT NULL,
	Container ENTITYID NOT NULL,
	StackTrace TEXT NOT NULL,
	StackTraceHash VARCHAR(50) NOT NULL,
	AssignedTo USERID,
	BugNumber INT,

	CONSTRAINT PK_ExceptionStackTrace PRIMARY KEY (ExceptionStackTraceId),
	CONSTRAINT UQ_ExceptionStackTraceId_StackTraceHashContainer UNIQUE (StackTraceHash, Container),
	CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(UserId)
	)
GO


CREATE TABLE mothership.ServerInstallation
	(
	ServerInstallationId INT IDENTITY(1,1) NOT NULL,
	ServerInstallationGUID ENTITYID NOT NULL,
	Description VARCHAR(100),
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
	CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
	CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	)
GO

CREATE TABLE mothership.ServerSession
	(
	ServerSessionId INT IDENTITY(1,1) NOT NULL,
	ServerInstallationId INT,
	ServerSessionGUID ENTITYID NOT NULL,
	EarliestKnownTime DATETIME NOT NULL,
	LastKnownTime DATETIME NOT NULL,
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
	CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
	CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
	CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	)
GO

CREATE TABLE mothership.ExceptionReport
	(
	ExceptionReportId INT IDENTITY(1,1) NOT NULL,
	ExceptionStackTraceId INT,
	Created DATETIME DEFAULT GETDATE(),
	SVNRevision INT,
	URL VARCHAR(512),
	ServerSessionId INT NOT NULL,
	DatabaseProductName VARCHAR(200),
	DatabaseProductVersion VARCHAR(200),
	DatabaseDriverName VARCHAR(200),
	DatabaseDriverVersion VARCHAR(200),
	RuntimeOS VARCHAR(100),
	Username VARCHAR(50),
	Browser VARCHAR(100),

	CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
	CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
	CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
	)
GO

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
            MAX(r.SVNRevision) AS MaxSVNRevision,
            MIN(r.SVNRevision) AS MinSVNRevision,
            COUNT(r.ExceptionReportId) AS Instances,
            MAX(r.Created) AS LastReport,
            MIN(r.Created) AS FirstReport
        FROM
            mothership.ExceptionStackTrace a, mothership.ExceptionReport r
        WHERE
            a.ExceptionStackTraceId = r.ExceptionStackTraceId
        GROUP BY
            a.ExceptionStackTraceId
    ) q
    ON q.ExceptionStackTraceId = st.ExceptionStackTraceId
GO

DROP VIEW mothership.ExceptionSummary
GO
DROP TABLE mothership.ExceptionReport;
DROP TABLE mothership.ServerSession;
DROP TABLE mothership.ServerInstallation;
DROP TABLE mothership.ExceptionStackTrace;

CREATE TABLE mothership.ExceptionStackTrace
	(
	ExceptionStackTraceId INT IDENTITY(1,1) NOT NULL,
	Container ENTITYID NOT NULL,
	StackTrace TEXT NOT NULL,
	StackTraceHash VARCHAR(50) NOT NULL,
	AssignedTo USERID,
	BugNumber INT,

	CONSTRAINT PK_ExceptionStackTrace PRIMARY KEY (ExceptionStackTraceId),
	CONSTRAINT UQ_ExceptionStackTraceId_StackTraceHashContainer UNIQUE (StackTraceHash, Container),
	CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
	CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(UserId)
	)
GO


CREATE TABLE mothership.ServerInstallation
	(
	ServerInstallationId INT IDENTITY(1,1) NOT NULL,
	ServerInstallationGUID ENTITYID NOT NULL,
	Note VARCHAR(100),
	Container ENTITYID NOT NULL,
	SystemDescription VARCHAR(200),
	LogoLink VARCHAR(200),
	OrganizationName VARCHAR(200),
	SystemShortName VARCHAR(200),
	ServerIP VARCHAR(20),

	CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
	CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
	CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	)
GO

CREATE TABLE mothership.ServerSession
	(
	ServerSessionId INT IDENTITY(1,1) NOT NULL,
	ServerInstallationId INT,
	ServerSessionGUID ENTITYID NOT NULL,
	EarliestKnownTime DATETIME NOT NULL,
	LastKnownTime DATETIME NOT NULL,
	Container ENTITYID NOT NULL,
	DatabaseProductName VARCHAR(200),
	DatabaseProductVersion VARCHAR(200),
	DatabaseDriverName VARCHAR(200),
	DatabaseDriverVersion VARCHAR(200),
	RuntimeOS VARCHAR(100),
	SVNRevision INT,

	CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
	CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
	CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
	CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
	)
GO

CREATE TABLE mothership.ExceptionReport
	(
	ExceptionReportId INT IDENTITY(1,1) NOT NULL,
	ExceptionStackTraceId INT,
	Created DATETIME DEFAULT GETDATE(),
	URL VARCHAR(512),
	ServerSessionId INT NOT NULL,
	Username VARCHAR(50),
	Browser VARCHAR(100),

	CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
	CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
	CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
	)
GO

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
    ON q.ExceptionStackTraceId = st.ExceptionStackTraceId
GO

CREATE VIEW mothership.ExceptionReportSummary AS
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
        mothership.ExceptionReport r, mothership.ServerSession ss, mothership.ExceptionStackTrace st
    WHERE
        r.ServerSessionId = ss.ServerSessionId
        AND r.ExceptionStackTraceId = st.ExceptionStackTraceId
GO

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

ALTER TABLE mothership.ServerInstallation ADD ServerHostName VARCHAR(256)
GO
ALTER TABLE mothership.ExceptionStackTrace ADD Comments TEXT
GO
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
        i.ServerInstallationId = s.ServerInstallationId
GO

CREATE TABLE mothership.SoftwareRelease
	(
	ReleaseId INT IDENTITY(1,1) NOT NULL,
	SVNRevision INT NOT NULL,
	Description VARCHAR(50) NOT NULL,
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_SoftwareRelease PRIMARY KEY (ReleaseId),
	CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision)
	)
GO


ALTER TABLE mothership.ExceptionReport ADD PageflowName VARCHAR(30)
GO

ALTER TABLE mothership.ExceptionReport ADD PageflowAction VARCHAR(40)
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

update mothership.serverinstallation set serverhostname=serverip where serverhostname is null
GO

delete from mothership.serverinstallation where serverinstallationid not in (select serverinstallationid from mothership.serversession)
GO

CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(serverinstallationid)
GO
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(exceptionstacktraceid)
GO
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(serversessionid)
GO

CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(container)
GO
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container)
GO
