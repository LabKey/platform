/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

/* mothership-0.00-2.10.sql */

CREATE SCHEMA mothership;

CREATE TABLE mothership.ExceptionStackTrace
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


CREATE TABLE mothership.ServerInstallation
(
	ServerInstallationId SERIAL NOT NULL,
	ServerInstallationGUID ENTITYID NOT NULL,
	Description VARCHAR(100),
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
	CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
	CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE TABLE mothership.ServerSession
(
	ServerSessionId SERIAL NOT NULL,
	ServerInstallationId INT,
	ServerSessionGUID ENTITYID NOT NULL,
	EarliestKnownTime TIMESTAMP NOT NULL,
	LastKnownTime TIMESTAMP NOT NULL,
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
	CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
	CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
	CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE TABLE mothership.ExceptionReport
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
	CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
	CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
);


DROP TABLE mothership.ExceptionReport;
DROP TABLE mothership.ServerSession;
DROP TABLE mothership.ServerInstallation;
DROP TABLE mothership.ExceptionStackTrace;

CREATE TABLE mothership.ExceptionStackTrace
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


CREATE TABLE mothership.ServerInstallation
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

CREATE TABLE mothership.ServerSession
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
	CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
	CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE TABLE mothership.ExceptionReport
(
	ExceptionReportId SERIAL NOT NULL,
	ExceptionStackTraceId INT,
	Created TIMESTAMP DEFAULT now(),
	URL VARCHAR(512),
	ServerSessionId INT NOT NULL,
	Username VARCHAR(50),
	Browser VARCHAR(100),

	CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
	CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
	CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
);


ALTER TABLE mothership.ExceptionReport ADD COLUMN ReferrerURL VARCHAR(512);
ALTER TABLE mothership.ServerInstallation ADD COLUMN ServerHostName VARCHAR(256);
ALTER TABLE mothership.ExceptionStackTrace ADD COLUMN Comments TEXT;

CREATE TABLE mothership.SoftwareRelease
(
	ReleaseId SERIAL NOT NULL,
	SVNRevision INT NOT NULL,
	Description VARCHAR(50) NOT NULL,
	Container ENTITYID NOT NULL,

	CONSTRAINT PK_SoftwareRelease PRIMARY KEY (ReleaseId),
	CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision)
);

ALTER TABLE mothership.ExceptionReport ADD COLUMN PageflowName VARCHAR(30);
ALTER TABLE mothership.ExceptionReport ADD COLUMN PageflowAction VARCHAR(40);

DELETE FROM mothership.serverinstallation WHERE serverinstallationid NOT IN (SELECT serverinstallationid FROM mothership.serversession);

CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(serverinstallationid);
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(exceptionstacktraceid);
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(serversessionid);

CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(container);
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container);

/* mothership-2.10-2.20.sql */

ALTER TABLE mothership.ExceptionStackTrace DROP CONSTRAINT uq_exceptionstacktraceid_stacktracehashcontainer;

/* mothership-2.20-2.30.sql */

ALTER TABLE mothership.ServerSession ADD JavaVersion varchar(100);
ALTER TABLE mothership.ServerSession ADD UserCount INT;
ALTER TABLE mothership.ServerSession ADD ActiveUserCount INT;
ALTER TABLE mothership.ServerSession ADD ProjectCount INT;
ALTER TABLE mothership.ServerSession ADD ContainerCount INT;
ALTER TABLE mothership.ServerSession ADD AdministratorEmail VARCHAR(100);