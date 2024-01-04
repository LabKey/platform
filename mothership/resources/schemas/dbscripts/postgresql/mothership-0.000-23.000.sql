/*
 * Copyright (c) 2019 LabKey Corporation
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
    CONSTRAINT FK_SoftwareRelease_Container FOREIGN KEY (Container) REFERENCES core.containers(ENTITYID)
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
    CONSTRAINT FK_ExceptionStackTrace_Container FOREIGN KEY (Container) REFERENCES core.Containers(ENTITYID),
    CONSTRAINT FK_ExceptionStackTrace_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES core.Usersdata(USERID),
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
    CONSTRAINT FK_ServerInstallation_Container FOREIGN KEY (Container) REFERENCES core.Containers(ENTITYID)
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
    CONSTRAINT FK_ServerSession_Container FOREIGN KEY (Container) REFERENCES core.Containers(ENTITYID),
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

ALTER TABLE mothership.ServerInstallation ADD COLUMN UsedInstaller BOOLEAN;

/* mothership-13.20-13.30.sql */

-- Add DESCRIPTION column to unique constraint on table SoftwareRelease. Support for enhancement 18609

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease;

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE
(
    Container,
    SVNRevision,
    SVNURL,
    Description
);

ALTER TABLE mothership.ServerInstallation ALTER COLUMN Note TYPE VARCHAR(500);

/* mothership-14.31-14.32.sql */

ALTER TABLE mothership.ServerInstallation ALTER COLUMN ServerInstallationGUID TYPE VARCHAR(1000);

/* mothership-16.20-16.30.sql */

ALTER TABLE mothership.ServerSession ADD COLUMN Distribution VARCHAR(500) NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN JsonMetrics VARCHAR NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN UsageReportingLevel VARCHAR(10) NULL;
ALTER TABLE mothership.ServerSession ADD COLUMN ExceptionReportingLevel VARCHAR(10) NULL;

/* mothership-16.30-17.10.sql */

CREATE INDEX IX_ExceptionReport_Created ON mothership.exceptionreport(created DESC);
ALTER TABLE mothership.serverinstallation ADD COLUMN IgnoreExceptions BOOLEAN;

ALTER TABLE mothership.exceptionstacktrace
  ADD COLUMN LastReport TIMESTAMP,
  ADD COLUMN FirstReport TIMESTAMP,
  ADD COLUMN Instances INT;

UPDATE mothership.ExceptionStackTrace est
SET instances = x.Instances, LastReport = x.LastReport, FirstReport= x.FirstReport
FROM (SELECT exceptionStackTraceId, count(exceptionReportId) Instances, max(created) LastReport, min(created) FirstReport FROM mothership.ExceptionReport GROUP BY ExceptionStackTraceId) x
WHERE est.ExceptionStackTraceId = x.ExceptionStackTraceId;

/* mothership-17.20-17.30.sql */

ALTER TABLE mothership.exceptionreport
    ADD COLUMN ErrorCode VARCHAR(6);

CREATE INDEX IX_ExceptionReport_ErrorCode ON mothership.exceptionreport (ErrorCode);

/* mothership-18.30-19.10.sql */

ALTER TABLE mothership.ServerSession
  ADD COLUMN buildTime timestamp;

SELECT core.fn_dropifexists('ServerSession', 'mothership', 'COLUMN', 'buildTime');

ALTER TABLE mothership.ServerSession
  ADD COLUMN buildTime timestamp;

/* mothership-19.20-19.30.sql */

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease;

ALTER TABLE mothership.SoftwareRelease ADD COLUMN BuildTime TIMESTAMP;
ALTER TABLE mothership.SoftwareRelease ADD COLUMN VcsTag VARCHAR(100);
ALTER TABLE mothership.SoftwareRelease ADD COLUMN VcsBranch VARCHAR(100);

UPDATE mothership.SoftwareRelease SET BuildTime = (SELECT MIN(ss.BuildTime) FROM mothership.ServerSession ss WHERE ss.SoftwareReleaseId = mothership.SoftwareRelease.SoftwareReleaseId);

ALTER TABLE mothership.ServerSession DROP COLUMN BuildTime;

ALTER TABLE mothership.SoftwareRelease RENAME SvnRevision TO VcsRevision;
ALTER TABLE mothership.SoftwareRelease RENAME SvnUrl TO VcsUrl;
ALTER TABLE mothership.SoftwareRelease RENAME Description TO BuildNumber;

ALTER TABLE mothership.SoftwareRelease ALTER COLUMN VcsRevision TYPE VARCHAR(40);

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, VcsRevision, VcsUrl, VcsBranch, VcsTag, BuildTime);

ALTER TABLE mothership.ServerSession ALTER COLUMN JsonMetrics TYPE JSONB USING JsonMetrics::JSONB;

/* 22.xxx SQL scripts */

ALTER TABLE mothership.ServerSession ADD COLUMN ServerHostName VARCHAR(256);
UPDATE mothership.ServerSession SET ServerHostName = (SELECT ServerHostName FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);

ALTER TABLE mothership.ServerSession ADD COLUMN ServerIP VARCHAR(20);
UPDATE mothership.ServerSession SET ServerIP = (SELECT ServerIP FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);

ALTER TABLE mothership.ServerSession ADD COLUMN OriginalServerSessionId INT;
CREATE INDEX IX_ServerSession_OriginalServerSessionId ON mothership.ServerSession(OriginalServerSessionId);
ALTER TABLE mothership.ServerSession
    ADD CONSTRAINT FK_ServerSession_OriginalServerSessionId FOREIGN KEY (OriginalServerSessionId)
        REFERENCES mothership.ServerSession (ServerSessionId);

ALTER TABLE mothership.ServerSession RENAME COLUMN ActiveUserCount TO RecentUserCount;

-- Migrate potentially mutable fields from install to session level
ALTER TABLE mothership.ServerSession ADD COLUMN OrganizationName VARCHAR(200);
UPDATE mothership.ServerSession SET OrganizationName = (SELECT OrganizationName FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);
ALTER TABLE mothership.ServerInstallation DROP COLUMN OrganizationName;

ALTER TABLE mothership.ServerSession ADD COLUMN SystemShortName VARCHAR(200);
UPDATE mothership.ServerSession SET SystemShortName = (SELECT SystemShortName FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);
ALTER TABLE mothership.ServerInstallation DROP COLUMN SystemShortName;

ALTER TABLE mothership.ServerSession ADD COLUMN SystemDescription VARCHAR(200);
UPDATE mothership.ServerSession SET SystemDescription = (SELECT SystemDescription FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);
ALTER TABLE mothership.ServerInstallation DROP COLUMN SystemDescription;

ALTER TABLE mothership.ServerSession ADD COLUMN LogoLink VARCHAR(200);
UPDATE mothership.ServerSession SET LogoLink = (SELECT LogoLink FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);
ALTER TABLE mothership.ServerInstallation DROP COLUMN LogoLink;

-- We don't care about tracking this anymore
ALTER TABLE mothership.ServerInstallation DROP COLUMN UsedInstaller;

-- We only want to track this at the session level
ALTER TABLE mothership.ServerInstallation DROP COLUMN ServerIp;

-- Going forward, we identify servers based on the combination of GUID (from the DB) and their host name
-- This helps differentiate staging from production and other cases where a copy of the same DB is used by multiple servers
ALTER TABLE mothership.ServerInstallation DROP CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID;
ALTER TABLE mothership.ServerInstallation ADD CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID_ServerHostName UNIQUE (ServerInstallationGUID, ServerHostName);

-- Create new install records for every host name we've seen
INSERT INTO mothership.ServerInstallation (ServerInstallationGUID,
                                           Note,
                                           Container,
                                           ServerHostName,
                                           IgnoreExceptions)
SELECT DISTINCT ServerInstallationGUID,
                Note,
                si.Container,
                ss.ServerHostName,
                IgnoreExceptions
FROM mothership.ServerInstallation si
INNER JOIN mothership.ServerSession ss ON
    si.ServerInstallationId = ss.ServerInstallationId AND
    si.ServerHostName != ss.ServerHostName;

-- Point the sessions to the appropriate install based on their host name
UPDATE mothership.ServerSession ss SET ServerInstallationId =
    (SELECT si2.ServerInstallationId FROM mothership.ServerInstallation si1
                                     INNER JOIN mothership.serverinstallation si2 ON ss.serverhostname = si2.ServerHostName AND si1.serverinstallationguid = si2.serverinstallationguid
                                     WHERE si1.serverinstallationid = ss.serverinstallationid);