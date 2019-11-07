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

