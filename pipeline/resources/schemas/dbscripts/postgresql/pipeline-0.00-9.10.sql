/*
 * Copyright (c) 2010 LabKey Corporation
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
     Info VARCHAR(255),
     FilePath VARCHAR(500),
     Email VARCHAR(255),

     CONSTRAINT PK_StatusFiles PRIMARY KEY (RowId)
);


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

    CONSTRAINT PK_PipelineRoots PRIMARY KEY (PipelineRootId)
);

ALTER TABLE pipeline.StatusFiles
    ADD Description VARCHAR(255),
    ADD DataUrl VARCHAR(255),
    ADD Job VARCHAR(36),
    ADD Provider VARCHAR(255);

ALTER TABLE pipeline.PipelineRoots
    ADD Type VARCHAR(255) NOT NULL DEFAULT 'PRIMARY';

ALTER TABLE pipeline.StatusFiles
    ADD HadError boolean;

ALTER TABLE pipeline.StatusFiles
    DROP COLUMN HadError;

ALTER TABLE pipeline.StatusFiles
    ADD HadError boolean NOT NULL DEFAULT FALSE;

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_Container FOREIGN KEY (Container) REFERENCES core.Containers (EntityId);

ALTER TABLE pipeline.StatusFiles
    ALTER FilePath TYPE VARCHAR(1024);
ALTER TABLE pipeline.StatusFiles
    ALTER Info TYPE VARCHAR(1024);
ALTER TABLE pipeline.StatusFiles
    ALTER DataUrl TYPE VARCHAR(1024);

CREATE INDEX IX_StatusFiles_Container ON pipeline.StatusFiles(Container);

ALTER TABLE pipeline.StatusFiles ADD COLUMN JobParent VARCHAR(36);
ALTER TABLE pipeline.StatusFiles ADD COLUMN JobStore TEXT;

ALTER TABLE pipeline.PipelineRoots ADD COLUMN KeyBytes BYTEA;
ALTER TABLE pipeline.PipelineRoots ADD COLUMN CertBytes BYTEA;
ALTER TABLE pipeline.PipelineRoots ADD COLUMN KeyPassword VARCHAR(32);

ALTER TABLE pipeline.PipelineRoots ADD COLUMN PerlPipeline boolean NOT NULL DEFAULT FALSE;

ALTER TABLE pipeline.StatusFiles ADD COLUMN ActiveTaskId VARCHAR(255);

DELETE FROM pipeline.statusfiles WHERE filepath IN (SELECT filepath FROM (SELECT COUNT(rowid) AS c, filepath FROM pipeline.statusfiles GROUP BY filepath) x WHERE c > 1)
    AND rowid NOT IN (SELECT rowid FROM (SELECT COUNT(rowid) AS c, MIN(rowid) AS rowid, filepath FROM pipeline.statusfiles GROUP BY filepath) y WHERE c > 1);

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_FilePath UNIQUE (FilePath);

CREATE INDEX IX_StatusFiles_Job ON pipeline.StatusFiles (Job);

CREATE INDEX IX_StatusFiles_JobParent ON pipeline.StatusFiles (JobParent);

/* pipeline-8.30-9.10.sql */

/* pipeline-8.30-8.31.sql */

UPDATE pipeline.StatusFiles SET JobParent = NULL
    WHERE JobParent NOT IN (SELECT Job FROM pipeline.StatusFiles WHERE Job IS NOT NULL);

UPDATE pipeline.StatusFiles SET Job = EntityId WHERE Job IS NULL;

/* pipeline-8.31-8.32.sql */

SELECT core.fn_dropifexists('StatusFiles', 'pipeline', 'CONSTRAINT', 'FK_StatusFiles_JobParent');

/* pipeline-8.32-8.33.sql */

SELECT core.fn_dropifexists('StatusFiles', 'pipeline', 'CONSTRAINT', 'UQ_StatusFiles_Job');

ALTER TABLE pipeline.StatusFiles ADD CONSTRAINT UQ_StatusFiles_Job UNIQUE (Job);

ALTER TABLE pipeline.StatusFiles
    ADD CONSTRAINT FK_StatusFiles_JobParent FOREIGN KEY (JobParent) REFERENCES pipeline.StatusFiles(Job);