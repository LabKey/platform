/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
CREATE CLUSTERED INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles (Container ASC, JobParent ASC);

ALTER TABLE pipeline.StatusFiles ADD ActiveHostName NVARCHAR(255) NULL;
ALTER TABLE pipeline.StatusFiles ADD TaskPipelineId NVARCHAR(255);

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
    Path NVARCHAR(300) NOT NULL,
    Providers VARCHAR(100),
    Type NVARCHAR(255) NOT NULL DEFAULT 'PRIMARY',

    KeyBytes IMAGE,
    CertBytes IMAGE,
    KeyPassword NVARCHAR(32),

    Searchable BIT NOT NULL DEFAULT 0,

    CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
);

ALTER TABLE pipeline.PipelineRoots ADD SupplementalPath NVARCHAR(300);

ALTER TABLE pipeline.PipelineRoots DROP COLUMN KeyBytes;
ALTER TABLE pipeline.PipelineRoots DROP COLUMN CertBytes;
ALTER TABLE pipeline.PipelineRoots DROP COLUMN KeyPassword;

DELETE FROM pipeline.PipelineRoots WHERE Container NOT IN (SELECT EntityId FROM core.Containers);

ALTER TABLE pipeline.PipelineRoots ADD CONSTRAINT FK_PipelineRoots_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

ALTER TABLE pipeline.PipelineRoots ADD CONSTRAINT UQ_PipelineRoots_Container_Type UNIQUE (Container, Type);

CREATE TABLE pipeline.TriggerConfigurations
(
  RowId INT IDENTITY(1, 1) NOT NULL,
  Container ENTITYID NOT NULL,
  Created DATETIME,
  CreatedBy USERID,
  Modified DATETIME,
  ModifiedBy USERID,

  Name NVARCHAR(255) NOT NULL,
  Description NVARCHAR(MAX),
  Type NVARCHAR(255) NOT NULL,
  Enabled BIT,
  Configuration NVARCHAR(MAX),
  PipelineId NVARCHAR(255) NOT NULL,
  LastChecked DATETIME,

  CONSTRAINT PK_TriggerConfigurations PRIMARY KEY (RowId),
  CONSTRAINT FK_TriggerConfigurations_Container FOREIGN KEY (Container) REFERENCES core.Containers (ENTITYID),
  CONSTRAINT UQ_TriggerConfigurations_Name UNIQUE (Container, Name)
);

ALTER TABLE pipeline.TriggerConfigurations ADD customConfiguration NVARCHAR(MAX);

CREATE TABLE pipeline.TriggeredFiles
(
  RowId INT IDENTITY(1,1) NOT NULL,
  Container ENTITYID NOT NULL,
  TriggerId INT NOT NULL,
  FilePath NVARCHAR(1000) NOT NULL,
  LastRun DATETIME,

  CONSTRAINT PK_TriggeredFiles PRIMARY KEY (RowId),
  CONSTRAINT FK_TriggeredFiles_TriggerId FOREIGN KEY (TriggerId) REFERENCES pipeline.TriggerConfigurations(RowId),
  CONSTRAINT FK_TriggeredFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (ENTITYID),
  CONSTRAINT UQ_TriggeredFiles_Container_TriggerId_FilePath UNIQUE (Container, TriggerId, FilePath)
);
