/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

CREATE TABLE pipeline.StatusFiles
(
    _ts TIMESTAMP DEFAULT now(),
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    ModifiedBy USERID,
    Modified TIMESTAMP DEFAULT now(),

    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,

    RowId SERIAL,
    Status VARCHAR(100),
    Info VARCHAR(1024),
    FilePath VARCHAR(1024),
    Email VARCHAR(255),

    Description VARCHAR(255),
    DataUrl VARCHAR(1024),
    Job VARCHAR(36),
    Provider VARCHAR(255),

    HadError BOOLEAN NOT NULL DEFAULT FALSE,
    JobParent VARCHAR(36),
    JobStore TEXT,
    ActiveTaskId VARCHAR(255),

    CONSTRAINT PK_StatusFiles PRIMARY KEY (RowId),
    CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId),
    CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath),
    CONSTRAINT UQ_StatusFiles_Job UNIQUE (Job),
    CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(Job)
);
CREATE INDEX IX_StatusFiles_Container_JobParent ON pipeline.StatusFiles (Container, JobParent);

CREATE TABLE pipeline.PipelineRoots
(
    _ts TIMESTAMP DEFAULT now(),
    CreatedBy USERID,
    Created TIMESTAMP DEFAULT now(),
    ModifiedBy USERID,
    Modified TIMESTAMP DEFAULT now(),

    Container ENTITYID NOT NULL,
    EntityId ENTITYID NOT NULL,

    PipelineRootId SERIAL,
    Path VARCHAR(300) NOT NULL,
    Providers VARCHAR(100),

    KeyBytes BYTEA,
    CertBytes BYTEA,
    KeyPassword VARCHAR(32),
    Type VARCHAR(255) NOT NULL DEFAULT 'PRIMARY',
    Searchable BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
);
