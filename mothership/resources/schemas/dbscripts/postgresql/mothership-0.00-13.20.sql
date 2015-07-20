/*
 * Copyright (c) 2015 LabKey Corporation
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

/* mothership-0.00-12.10.sql */

CREATE SCHEMA mothership;

CREATE TABLE mothership.SoftwareRelease
(
    SoftwareReleaseId SERIAL,
    SVNRevision INT NULL,
    Description VARCHAR(50) NOT NULL,
    Container ENTITYID NOT NULL,
    SVNURL VARCHAR(200),

    CONSTRAINT PK_SoftwareRelease PRIMARY KEY (SoftwareReleaseId),
    CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision, SVNURL),
    CONSTRAINT FK_SoftwareRelease_Container FOREIGN KEY (Container) REFERENCES core.containers(EntityId)
);

CREATE TABLE mothership.ExceptionStackTrace
(
    ExceptionStackTraceId SERIAL NOT NULL,
    Container ENTITYID NOT NULL,
    StackTrace TEXT NOT NULL,
    StackTraceHash VARCHAR(50) NOT NULL,
    AssignedTo USERID,
    BugNumber INT,
    Comments TEXT,

    CONSTRAINT PK_ExceptionStackTrace PRIMARY KEY (ExceptionStackTraceId),
    CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(UserId),
    CONSTRAINT UQ_ExceptionStackTrace_Container_Hash UNIQUE (Container, StackTraceHash)
);
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(container);

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
    ServerHostName VARCHAR(256),

    CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
    CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
    CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);
CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(Container);

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

    JavaVersion VARCHAR(100),
    UserCount INT,
    ActiveUserCount INT,
    ProjectCount INT,
    ContainerCount INT,
    AdministratorEmail VARCHAR(100),
    EnterprisePipelineEnabled BOOLEAN,
    SoftwareReleaseId INT NOT NULL,
    HeapSize INT,
    ServletContainer VARCHAR(100),

    CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
    CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
    CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
    CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ServerSession_SoftwareRelease FOREIGN KEY (SoftwareReleaseId) REFERENCES mothership.SoftwareRelease(SoftwareReleaseId)
);
CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.serversession(ServerInstallationId);

CREATE TABLE mothership.ExceptionReport
(
    ExceptionReportId SERIAL NOT NULL,
    ExceptionStackTraceId INT,
    Created TIMESTAMP DEFAULT now(),
    URL VARCHAR(512),
    ServerSessionId INT NOT NULL,
    Username VARCHAR(50),
    Browser VARCHAR(100),

    ReferrerURL VARCHAR(512),
    PageflowName VARCHAR(30),
    PageflowAction VARCHAR(40),
    SQLState VARCHAR(100),
    ExceptionMessage VARCHAR(1000),  -- Store the exception's message, which would otherwise be lost when we de-dupe stack traces

    CONSTRAINT PK_ExceptionReport PRIMARY KEY (ExceptionReportId),
    CONSTRAINT FK_ExceptionReport_ExceptionStackTrace FOREIGN KEY (ExceptionStackTraceId) REFERENCES mothership.ExceptionStackTrace(ExceptionStackTraceId),
    CONSTRAINT FK_ExceptionReport_ServerSessionId FOREIGN KEY (ServerSessionId) REFERENCES mothership.ServerSession(ServerSessionId)
);
CREATE INDEX IX_ExceptionReport_ExceptionStackTraceId ON mothership.exceptionreport(ExceptionStackTraceId);
CREATE INDEX IX_ExceptionReport_ServerSessionId ON mothership.exceptionreport(ServerSessionId);

/* mothership-13.10-13.20.sql */

ALTER TABLE mothership.ExceptionStackTrace ADD COLUMN ModifiedBy USERID;
ALTER TABLE mothership.ExceptionStackTrace ADD COLUMN Modified TIMESTAMP;

ALTER TABLE mothership.serverinstallation ADD COLUMN usedinstaller boolean;