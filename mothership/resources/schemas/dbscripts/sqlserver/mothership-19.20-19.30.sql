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

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease;

ALTER TABLE mothership.SoftwareRelease ADD BuildTime DATETIME;
ALTER TABLE mothership.SoftwareRelease ADD VcsTag NVARCHAR(100);
ALTER TABLE mothership.SoftwareRelease ADD VcsBranch NVARCHAR(100);

GO

UPDATE mothership.SoftwareRelease SET BuildTime = (SELECT MIN(ss.BuildTime) FROM mothership.ServerSession ss WHERE ss.SoftwareReleaseId = mothership.SoftwareRelease.SoftwareReleaseId);

ALTER TABLE mothership.ServerSession DROP COLUMN BuildTime;

EXEC sp_rename 'mothership.SoftwareRelease.SvnRevision', 'VcsRevision', 'COLUMN';
EXEC sp_rename 'mothership.SoftwareRelease.SvnUrl', 'VcsUrl', 'COLUMN';
EXEC sp_rename 'mothership.SoftwareRelease.Description', 'BuildNumber', 'COLUMN';

ALTER TABLE mothership.SoftwareRelease ALTER COLUMN VcsRevision NVARCHAR(40);

GO

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE (Container, VcsRevision, VcsUrl, VcsBranch, VcsTag, BuildTime);
