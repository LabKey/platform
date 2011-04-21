/*
 * Copyright (c) 2011 LabKey Corporation
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

/* pipeline-0.00-8.30.sql */

EXEC sp_addapprole 'pipeline', 'password'
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
    Info NVARCHAR(255),
    FilePath NVARCHAR(500),
    Email NVARCHAR(255)
)
GO

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

    CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
)
GO

ALTER TABLE pipeline.StatusFiles ADD
    Description NVARCHAR(255),
    DataUrl NVARCHAR(255),
    Job uniqueidentifier,
    Provider NVARCHAR(255)
GO

ALTER TABLE pipeline.PipelineRoots ADD
    Type NVARCHAR(255) NOT NULL DEFAULT 'PRIMARY'
GO

ALTER TABLE pipeline.StatusFiles ADD
    HadError bit NOT NULL DEFAULT 0
GO

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId)
GO

ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN FilePath VARCHAR(1024)
ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN Info VARCHAR(1024)
ALTER TABLE pipeline.StatusFiles
    ALTER COLUMN DataUrl VARCHAR(1024)
GO

ALTER TABLE pipeline.StatusFiles ADD
    JobParent uniqueidentifier,
    JobStore NTEXT
GO

ALTER TABLE pipeline.PipelineRoots ADD
    KeyBytes IMAGE,
    CertBytes IMAGE,
    KeyPassword NVARCHAR(32)
GO

ALTER TABLE pipeline.PipelineRoots ADD
    PerlPipeline bit NOT NULL DEFAULT 0
GO

ALTER TABLE pipeline.StatusFiles ADD
    ActiveTaskId NVARCHAR(255)
GO

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath)
GO

CREATE INDEX IX_StatusFiles_Job ON pipeline.StatusFiles (Job)
GO

CREATE INDEX IX_StatusFiles_JobParent ON pipeline.StatusFiles (JobParent)
GO

CREATE CLUSTERED INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container)
GO

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT pk_statusfiles PRIMARY KEY (RowId)
GO

/* pipeline-8.30-9.10.sql */

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_Job UNIQUE (Job)
GO

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(Job)
GO