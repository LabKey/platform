/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
/* mothership-13.21-13.22.sql */

-- Add DESCRIPTION column to unique constraint on tble SoftwareRelease. Support for enhancment 18609

ALTER TABLE mothership.SoftwareRelease DROP CONSTRAINT UQ_SoftwareRelease
GO

ALTER TABLE mothership.SoftwareRelease ADD CONSTRAINT UQ_SoftwareRelease UNIQUE
(
	Container,
	SVNRevision,
	SVNURL,
	Description
)

/* mothership-13.22-13.23.sql */

ALTER TABLE mothership.ServerInstallation ALTER COLUMN Note varchar(500) NULL