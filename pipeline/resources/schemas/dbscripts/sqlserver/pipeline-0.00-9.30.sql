/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

/* pipeline-0.00-9.10.sql */

CREATE SCHEMA pipeline;
GO

CREATE TABLE pipeline.StatusFiles
(
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME DEFAULT GETDATE(),
    ModifiedBy USERID,
    Modified DATETIME DEFAULT GETDATE(),

    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,

    RowId INT IDENTITY(1,1) NOT NULL,
    Status NVARCHAR(100),
    Info NVARCHAR(1024),
    FilePath NVARCHAR(1024),
    Email NVARCHAR(255),

    Description NVARCHAR(255),
    DataUrl NVARCHAR(1024),
    Job UNIQUEIDENTIFIER,
    Provider NVARCHAR(255),
    HadError BIT NOT NULL DEFAULT 0,

    JobParent UNIQUEIDENTIFIER,
    JobStore NTEXT,
    ActiveTaskId NVARCHAR(255),

    CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath),
    CONSTRAINT PK_StatusFiles PRIMARY KEY NONCLUSTERED (RowId),  -- Clustered on Container below
    CONSTRAINT UQ_StatusFiles_Job UNIQUE (Job),
    CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(Job)
);
CREATE INDEX IX_StatusFiles_Job ON pipeline.StatusFiles (Job);
CREATE INDEX IX_StatusFiles_JobParent ON pipeline.StatusFiles (JobParent);
CREATE CLUSTERED INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container);

CREATE TABLE pipeline.PipelineRoots
(
    _ts TIMESTAMP,
    CreatedBy USERID,
    Created DATETIME DEFAULT GETDATE(),
    ModifiedBy USERID,
    Modified DATETIME DEFAULT GETDATE(),

    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,

    PipelineRootId INT IDENTITY(1,1) NOT NULL,
    Path  NVARCHAR(300) NOT NULL,
    Providers VARCHAR(100),
    Type NVARCHAR(255) NOT NULL DEFAULT 'PRIMARY',

    KeyBytes IMAGE,
    CertBytes IMAGE,
    KeyPassword NVARCHAR(32),
    PerlPipeline BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
);

/* pipeline-9.20-9.30.sql */

DECLARE @default sysname
SELECT @default = object_name(cdefault)
FROM syscolumns
WHERE id = object_id('pipeline.PipelineRoots')
AND name = 'PerlPipeline';
EXEC ('ALTER TABLE pipeline.PipelineRoots DROP CONSTRAINT ' + @default);

ALTER TABLE pipeline.PipelineRoots DROP COLUMN PerlPipeline;

-- drop this index because it is already a unique constraint
-- format of Drop proc
-- exec core.fn_dropifexists <tablename>, <schemaname>, 'INDEX', <indexname>
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Job';

-- drop this index because we can change the code to always pass container with jobparent
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_JobParent';

-- drop this index so we can add the jobparent column to it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container';

-- in case this has alreaady been run, drop the new index before tryint to create it
EXEC core.fn_dropifexists 'StatusFiles', 'pipeline', 'INDEX', 'IX_StatusFiles_Container_JobParent';

CREATE CLUSTERED INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles (Container ASC, JobParent ASC);
