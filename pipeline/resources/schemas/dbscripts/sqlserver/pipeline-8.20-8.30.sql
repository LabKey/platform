/*
 * Copyright (c) 2008 LabKey Corporation
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

/* pipeline-8.20-8.21.sql */

ALTER TABLE pipeline.PipelineRoots ADD
    KeyBytes IMAGE,
    CertBytes IMAGE,
    KeyPassword NVARCHAR(32)
GO

/* pipeline-8.21-8.22.sql */

ALTER TABLE pipeline.PipelineRoots ADD
    PerlPipeline bit NOT NULL DEFAULT 0
GO

/* pipeline-8.22-8.23.sql */

ALTER TABLE pipeline.StatusFiles ADD
    ActiveTaskId NVARCHAR(255)
GO

/* pipeline-8.23-8.24.sql */

DELETE FROM pipeline.statusfiles WHERE filepath IN (SELECT filepath FROM (SELECT COUNT(rowid) AS c, filepath FROM pipeline.statusfiles GROUP BY filepath) x WHERE c > 1)
AND rowid NOT IN (SELECT rowid FROM (SELECT COUNT(rowid) AS c, MIN(rowid) AS rowid, filepath FROM pipeline.statusfiles GROUP BY filepath) y WHERE c > 1)
GO

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath)
GO

/* pipeline-8.24-8.25.sql */

CREATE INDEX IX_StatusFiles_Job ON pipeline.StatusFiles (Job)
GO

/* pipeline-8.261-8.27.sql */

CREATE INDEX IX_StatusFiles_JobParent ON pipeline.StatusFiles (JobParent)
GO

DROP INDEX pipeline.StatusFiles.IX_StatusFiles_Container
GO

ALTER TABLE pipeline.StatusFiles DROP CONSTRAINT pk_statusfiles
GO

CREATE CLUSTERED INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
GO

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT pk_statusfiles PRIMARY KEY (RowId)
GO

EXEC core.executeJavaUpgradeCode 'setPipelineToolsDirectory'
GO