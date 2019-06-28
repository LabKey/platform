/*
 * Copyright (c) 2017-2019 LabKey Corporation
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

/* mothership-14.30-14.31.sql */

EXEC core.executeJavaUpgradeCode 'reconfigureExceptionReporting';

/* mothership-14.31-14.32.sql */

ALTER TABLE mothership.ServerInstallation DROP CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID;
GO
ALTER TABLE mothership.ServerInstallation ALTER COLUMN ServerInstallationGUID NVARCHAR(1000) NOT NULL;
GO
ALTER TABLE mothership.ServerInstallation ADD CONSTRAINT UQ_ServerInstallation_ServerInstallationGUID UNIQUE (ServerInstallationGUID);