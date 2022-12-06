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

ALTER TABLE mothership.ServerSession ADD COLUMN ServerHostName VARCHAR(256);
UPDATE mothership.ServerSession SET ServerHostName = (SELECT ServerHostName FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);

ALTER TABLE mothership.ServerSession ADD COLUMN ServerIP VARCHAR(20);
UPDATE mothership.ServerSession SET ServerIP = (SELECT ServerIP FROM mothership.ServerInstallation si WHERE si.serverinstallationid = ServerSession.serverinstallationid);

ALTER TABLE mothership.ServerSession ADD COLUMN OriginalServerSessionId INT;
CREATE INDEX IX_ServerSession_OriginalServerSessionId ON mothership.ServerSession(OriginalServerSessionId);
ALTER TABLE mothership.ServerSession
    ADD CONSTRAINT FK_ServerSession_OriginalServerSessionId FOREIGN KEY (OriginalServerSessionId)
        REFERENCES mothership.ServerSession (ServerSessionId);
