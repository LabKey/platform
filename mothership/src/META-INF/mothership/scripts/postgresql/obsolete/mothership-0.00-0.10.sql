/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

CREATE SCHEMA mothership;
SET search_path TO mothership, public;  -- include public to get ENTITYID, USERID

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
	Description VARCHAR(100),
	Container ENTITYID NOT NULL,

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
            MAX(r.SVNRevision) AS MaxSVNRevision,
            MIN(r.SVNRevision) AS MinSVNRevision,
            COUNT(r.ExceptionReportId) AS Instances,
            MAX(r.Created) AS LastReport,
            MIN(r.Created) AS FirstReport
        FROM
            ExceptionStackTrace a, ExceptionReport r
        WHERE
            a.ExceptionStackTraceId = r.ExceptionStackTraceId
        GROUP BY
            a.ExceptionStackTraceId
    ) q
    ON q.ExceptionStackTraceId = st.ExceptionStackTraceId;