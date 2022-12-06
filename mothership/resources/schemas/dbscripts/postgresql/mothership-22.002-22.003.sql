/*
 * Copyright (c) 2022 LabKey Corporation
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

