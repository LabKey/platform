/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
GO

CREATE TABLE mothership.SoftwareRelease
(
    SoftwareReleaseId INT IDENTITY,
    SVNRevision INT NULL,
    Description VARCHAR(50) NOT NULL,
    Container ENTITYID NOT NULL,
    SVNURL NVARCHAR(200),

    CONSTRAINT PK_SoftwareRelease PRIMARY KEY (SoftwareReleaseId),
    CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, SVNRevision, SVNURL),
    CONSTRAINT FK_SoftwareRelease_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);

CREATE TABLE mothership.ExceptionStackTrace
(
    ExceptionStackTraceId INT IDENTITY(1,1) NOT NULL,
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
CREATE INDEX IX_ExceptionStackTrace_Container ON mothership.ExceptionStackTrace(Container);

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
    ServerHostName VARCHAR(256),

    CONSTRAINT PK_ServerInstallation PRIMARY KEY (ServerInstallationId),
    CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID),
    CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId)
);
CREATE INDEX IX_ServerInstallation_Container ON mothership.ServerInstallation(Container);

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

    JavaVersion VARCHAR(100),
    UserCount INT,
    ActiveUserCount INT,
    ProjectCount INT,
    ContainerCount INT,
    AdministratorEmail NVARCHAR(100),
    EnterprisePipelineEnabled BIT,
    SoftwareReleaseId INT NOT NULL,
    HeapSize INT,
    ServletContainer NVARCHAR(100),

    CONSTRAINT PK_ServerSession PRIMARY KEY (ServerSessionId),
    CONSTRAINT UQ_ServerSession_ServerSessionGUID UNIQUE (ServerSessionGUID),
    CONSTRAINT FK_ServerSession_ServerInstallation FOREIGN KEY (ServerInstallationId) REFERENCES mothership.ServerInstallation(ServerInstallationId),
    CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(EntityId),
    CONSTRAINT FK_ServerSession_SoftwareRelease FOREIGN KEY (SoftwareReleaseId) REFERENCES mothership.SoftwareRelease(SoftwareReleaseId)
);
CREATE INDEX IX_ServerSession_ServerInstallationId ON mothership.ServerSession(ServerInstallationId);

CREATE TABLE mothership.ExceptionReport
(
    ExceptionReportId INT IDENTITY(1,1) NOT NULL,
    ExceptionStackTraceId INT,
    Created DATETIME DEFAULT GETDATE(),
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

ALTER TABLE mothership.ExceptionStackTrace ADD ModifiedBy USERID;
ALTER TABLE mothership.ExceptionStackTrace ADD Modified DATETIME;

ALTER TABLE mothership.ServerInstallation ADD UsedInstaller BIT NULL;

/* mothership-13.20-13.30.sql */

-- Add DESCRIPTION column to unique constraint on table SoftwareRelease. Support for enhancement 18609

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease
GO

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE
(
    Container,
    SVNRevision,
    SVNURL,
    Description
)

ALTER TABLE mothership.ServerInstallation ALTER COLUMN Note VARCHAR(500) NULL